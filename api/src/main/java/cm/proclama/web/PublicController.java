package cm.proclama.web;

import cm.proclama.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Routes publiques : liste et statut (micro-cache), consultation, attestation. */
@RestController
@RequestMapping("/api")
public class PublicController {
  public record LookupReq(String matricule, String dob) {}

  private static final Logger log = LoggerFactory.getLogger(PublicController.class);
  private static final String CACHE = "public, max-age=1";
  private static final String NOT_FOUND_MSG = "Aucun resultat pour ces informations.";

  private final JdbcTemplate jdbc;
  private final SessionService sessions;
  private final Counters counters;
  private final RateLimiter limiter;
  private final S3Service s3;
  private final PdfService pdf;

  public PublicController(JdbcTemplate jdbc, SessionService sessions, Counters counters, RateLimiter limiter, S3Service s3,
      PdfService pdf) {
    this.jdbc = jdbc;
    this.sessions = sessions;
    this.counters = counters;
    this.limiter = limiter;
    this.s3 = s3;
    this.pdf = pdf;
  }

  @GetMapping("/sessions")
  public ResponseEntity<List<Map<String, Object>>> list() {
    counters.statuses.incrementAndGet();
    List<Map<String, Object>> rows = jdbc.query(
        "select code, title, " + SessionService.EFFECTIVE + " as effective_status, publish_at from sessions "
            + "where status <> 'draft' order by created_at desc",
        (rs, i) -> M.of("code", rs.getString("code"), "title", rs.getString("title"),
            "status", rs.getString("effective_status"), "publishAt", SessionService.ts(rs, "publish_at")));
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, CACHE).body(rows);
  }

  @GetMapping("/sessions/{code}/status")
  public ResponseEntity<Map<String, Object>> status(@PathVariable String code) {
    counters.statuses.incrementAndGet();
    SessionService.Row s = sessions.find(code).filter(r -> !"draft".equals(r.status()))
        .orElseThrow(() -> new ApiException(404, "not_found", "Session introuvable."));
    Map<String, Object> body = M.of("code", s.code(), "title", s.title(), "status", s.effective(),
        "publishAt", s.publishAt(), "serverTime", s.serverTime());
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, CACHE).body(body);
  }

  @PostMapping("/sessions/{code}/lookup")
  public Map<String, Object> lookup(@PathVariable String code, @RequestBody(required = false) LookupReq req,
      HttpServletRequest http) {
    counters.lookups.incrementAndGet();
    Found f = find(code, req, http);
    return M.of("session", M.of("code", f.sessionCode(), "title", f.sessionTitle()),
        "candidate", M.of("matricule", f.matricule(), "fullName", f.fullName()),
        "result", M.of("decision", f.decision(), "average", f.average(), "mention", f.mention()),
        "attestation", M.of("available", true));
  }

  @PostMapping("/sessions/{code}/attestation")
  public ResponseEntity<byte[]> attestation(@PathVariable String code, @RequestBody(required = false) LookupReq req,
      HttpServletRequest http) throws java.io.IOException {
    Found f = find(code, req, http);
    String key = "attestations/" + f.sessionCode() + "/" + f.candidateId() + ".pdf";
    byte[] data;
    if (s3.exists(key)) {
      data = s3.getBytes(key);
      log.info("attestation relue depuis le stockage");
    } else {
      data = pdf.build(f);
      s3.putBytes(key, data, "application/pdf");
      log.info("attestation generee puis enregistree");
    }
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attestation-" + f.matricule() + ".pdf\"")
        .body(data);
  }

  /** Validation -> limite de débit -> statut effectif -> requête unique candidat/résultat. */
  private Found find(String code, LookupReq req, HttpServletRequest http) {
    if (http.getContentLengthLong() > 2048) throw new ApiException(413, "too_large", "Requete trop volumineuse.");
    if (req == null || !CsvValidator.validMatricule(req.matricule()) || req.dob() == null) {
      throw new ApiException(422, "validation_error", "Matricule ou date de naissance au format invalide.");
    }
    LocalDate dob;
    try {
      dob = LocalDate.parse(req.dob());
    } catch (DateTimeParseException e) {
      throw new ApiException(422, "validation_error", "Matricule ou date de naissance au format invalide.");
    }
    MDC.put("matriculeHash", sha8(req.matricule()));
    int wait = limiter.check(ip(http));
    if (wait > 0) {
      throw new ApiException(429, "rate_limited", "Trop de tentatives.", Map.of(), Map.of("Retry-After", String.valueOf(wait)));
    }
    SessionService.Row s = sessions.find(code).filter(r -> !"draft".equals(r.status()))
        .orElseThrow(() -> new ApiException(404, "not_found", NOT_FOUND_MSG));
    if (!"published".equals(s.effective())) {
      Map<String, Object> extra = new HashMap<>();
      if (s.publishAt() != null) extra.put("publishAt", s.publishAt());
      throw new ApiException(409, "not_published", "Resultats non publies.", extra, Map.of());
    }
    List<Found> rows = jdbc.query(
        "select c.id, c.matricule, c.full_name, c.dob, r.decision, r.average, r.mention from candidates c "
            + "join results r on r.candidate_id = c.id where c.session_id = ? and c.matricule = ? and c.dob = ?",
        (rs, i) -> new Found(s.code(), s.title(), rs.getObject("id", java.util.UUID.class), rs.getString("matricule"),
            rs.getString("full_name"), rs.getObject("dob", LocalDate.class), rs.getString("decision"),
            rs.getBigDecimal("average"), rs.getString("mention")),
        s.id(), req.matricule(), dob);
    if (rows.isEmpty()) throw new ApiException(404, "not_found", NOT_FOUND_MSG);
    return rows.get(0);
  }

  private static String ip(HttpServletRequest r) {
    String x = r.getHeader("X-Forwarded-For");
    return (x != null && !x.isBlank()) ? x.split(",")[0].trim() : r.getRemoteAddr();
  }

  private static String sha8(String s) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 8);
    } catch (Exception e) {
      return "";
    }
  }
}
