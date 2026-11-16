package cm.proclama.web;

import cm.proclama.service.*;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Routes du personnel (operator, admin). Publication, planification et audit : admin seulement (RG9). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
  public record CreateReq(String code, String title) {}

  public record PatchReq(String title, String publishAt) {}

  public record ScheduleReq(String publishAt) {}

  private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9-]{3,30}$");

  private final JdbcTemplate jdbc;
  private final SessionService sessions;
  private final S3Service s3;
  private final AuditService audit;

  public AdminController(JdbcTemplate jdbc, SessionService sessions, S3Service s3, AuditService audit) {
    this.jdbc = jdbc;
    this.sessions = sessions;
    this.s3 = s3;
    this.audit = audit;
  }

  // ---------- Sessions ----------

  private static final String LIST_SQL = "select code, title, status, " + SessionService.EFFECTIVE
      + " as effective_status, publish_at, created_at, (select count(*) from candidates c where c.session_id = sessions.id) as candidates"
      + " from sessions";

  private static Map<String, Object> sessionMap(ResultSet rs) throws SQLException {
    return M.of("code", rs.getString("code"), "title", rs.getString("title"), "status", rs.getString("effective_status"),
        "storedStatus", rs.getString("status"), "publishAt", SessionService.ts(rs, "publish_at"),
        "candidates", rs.getLong("candidates"), "createdAt", SessionService.ts(rs, "created_at"));
  }

  @GetMapping("/sessions")
  public List<Map<String, Object>> list() {
    return jdbc.query(LIST_SQL + " order by created_at desc", (rs, i) -> sessionMap(rs));
  }

  @GetMapping("/sessions/{code}")
  public Map<String, Object> get(@PathVariable String code) {
    List<Map<String, Object>> r = jdbc.query(LIST_SQL + " where code = ?", (rs, i) -> sessionMap(rs), code);
    if (r.isEmpty()) throw notFound();
    return r.get(0);
  }

  @PostMapping("/sessions")
  public ResponseEntity<Map<String, Object>> create(@RequestBody(required = false) CreateReq r,
      @RequestAttribute("auth") AuthUser u) {
    if (r == null || r.code() == null || !CODE.matcher(r.code()).matches()) {
      throw new ApiException(422, "validation_error", "Code : 3 a 30 caracteres parmi lettres, chiffres et tirets.");
    }
    if (r.title() == null || r.title().isBlank() || r.title().length() > 200) {
      throw new ApiException(422, "validation_error", "Titre obligatoire (200 caracteres au plus).");
    }
    try {
      jdbc.update("insert into sessions(code, title) values (?, ?)", r.code(), r.title().trim());
    } catch (DuplicateKeyException e) {
      throw new ApiException(409, "conflict", "Ce code de session existe deja.");
    }
    audit.log(u, "session.create", r.code(), M.of("title", r.title()));
    return ResponseEntity.status(HttpStatus.CREATED).body(get(r.code()));
  }

  @PatchMapping("/sessions/{code}")
  public Map<String, Object> patch(@PathVariable String code, @RequestBody(required = false) PatchReq r,
      @RequestAttribute("auth") AuthUser u) {
    SessionService.Row s = session(code);
    if (r == null) throw new ApiException(422, "validation_error", "Corps manquant.");
    if (r.title() != null) {
      if (r.title().isBlank() || r.title().length() > 200) throw new ApiException(422, "validation_error", "Titre invalide.");
      jdbc.update("update sessions set title = ?, updated_at = now() where id = ?", r.title().trim(), s.id());
    }
    if (r.publishAt() != null) {
      requireAdmin(u);
      jdbc.update("update sessions set publish_at = ?, updated_at = now() where id = ?", parse(r.publishAt()), s.id());
    }
    audit.log(u, "session.update", code, M.of("title", r.title(), "publishAt", r.publishAt()));
    return get(code);
  }

  @PostMapping("/sessions/{code}/publish")
  public Map<String, Object> publish(@PathVariable String code, @RequestAttribute("auth") AuthUser u) {
    requireAdmin(u);
    SessionService.Row s = session(code);
    jdbc.update("update sessions set status = 'published', publish_at = now(), updated_at = now() where id = ?", s.id());
    audit.log(u, "session.publish", code, M.of());
    return get(code);
  }

  @PostMapping("/sessions/{code}/schedule")
  public Map<String, Object> schedule(@PathVariable String code, @RequestBody(required = false) ScheduleReq r,
      @RequestAttribute("auth") AuthUser u) {
    requireAdmin(u);
    SessionService.Row s = session(code);
    if (r == null || r.publishAt() == null) throw new ApiException(422, "validation_error", "publishAt obligatoire.");
    OffsetDateTime at = parse(r.publishAt());
    jdbc.update("update sessions set status = 'scheduled', publish_at = ?, updated_at = now() where id = ?", at, s.id());
    audit.log(u, "session.schedule", code, M.of("publishAt", r.publishAt()));
    return get(code);
  }

  @PostMapping("/sessions/{code}/unpublish")
  public Map<String, Object> unpublish(@PathVariable String code, @RequestAttribute("auth") AuthUser u) {
    requireAdmin(u);
    SessionService.Row s = session(code);
    jdbc.update("update sessions set status = 'unpublished', updated_at = now() where id = ?", s.id());
    audit.log(u, "session.unpublish", code, M.of());
    return get(code);
  }

  // ---------- Imports ----------

  @PostMapping(value = "/sessions/{code}/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> upload(@PathVariable String code, @RequestParam("file") MultipartFile file,
      @RequestAttribute("auth") AuthUser u) throws IOException {
    SessionService.Row s = session(code);
    if ("published".equals(s.effective())) {
      throw new ApiException(409, "conflict", "Session publiee : depubliez-la avant d'importer.");
    }
    String name = file.getOriginalFilename() == null ? "import.csv" : file.getOriginalFilename();
    if (name.length() > 200) name = name.substring(name.length() - 200);
    if (file.isEmpty() || !name.toLowerCase().endsWith(".csv")) {
      throw new ApiException(422, "validation_error", "Un fichier .csv non vide est attendu.");
    }
    UUID id = UUID.randomUUID();
    String key = "imports/" + code + "/" + id + ".csv"; // le nom du fichier n'est jamais une clé de stockage
    try (InputStream in = file.getInputStream()) {
      s3.put(key, in, file.getSize(), "text/csv");
    }
    jdbc.update("insert into imports(id, session_id, filename, object_key, created_by) values (?, ?, ?, ?, ?)",
        id, s.id(), name, key, u.id());
    audit.log(u, "import.upload", code, M.of("importId", id.toString(), "filename", name));
    return ResponseEntity.accepted().body(M.of("importId", id, "status", "pending"));
  }

  private static final String IMPORT_COLS =
      "select id, filename, status, rows_total, rows_ok, rows_error, errors_truncated, error_key, created_at, started_at, finished_at from imports";

  private static Map<String, Object> importMap(ResultSet rs) throws SQLException {
    return M.of("id", rs.getObject("id", UUID.class), "filename", rs.getString("filename"), "status", rs.getString("status"),
        "rowsTotal", rs.getInt("rows_total"), "rowsOk", rs.getInt("rows_ok"), "rowsError", rs.getInt("rows_error"),
        "errorsTruncated", rs.getBoolean("errors_truncated"), "hasReport", rs.getString("error_key") != null,
        "createdAt", SessionService.ts(rs, "created_at"), "startedAt", SessionService.ts(rs, "started_at"),
        "finishedAt", SessionService.ts(rs, "finished_at"));
  }

  @GetMapping("/sessions/{code}/imports")
  public List<Map<String, Object>> imports(@PathVariable String code) {
    SessionService.Row s = session(code);
    return jdbc.query(IMPORT_COLS + " where session_id = ? order by created_at desc limit 50", (rs, i) -> importMap(rs), s.id());
  }

  @GetMapping("/imports/{id}")
  public Map<String, Object> importDetail(@PathVariable UUID id) {
    List<Map<String, Object>> r = jdbc.query(IMPORT_COLS + " where id = ?", (rs, i) -> importMap(rs), id);
    if (r.isEmpty()) throw notFound();
    return r.get(0);
  }

  @GetMapping("/imports/{id}/errors")
  public ResponseEntity<byte[]> importErrors(@PathVariable UUID id) throws IOException {
    List<String> keys = jdbc.queryForList("select error_key from imports where id = ? and error_key is not null", String.class, id);
    if (keys.isEmpty()) throw new ApiException(404, "not_found", "Rapport indisponible.");
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"erreurs-" + id + ".csv\"")
        .body(s3.getBytes(keys.get(0)));
  }

  // ---------- Statistiques et audit ----------

  @GetMapping("/sessions/{code}/stats")
  public Map<String, Object> stats(@PathVariable String code) {
    SessionService.Row s = session(code);
    Long total = jdbc.queryForObject("select count(*) from candidates where session_id = ?", Long.class, s.id());
    Map<String, Long> dist = new LinkedHashMap<>();
    jdbc.query("select r.decision, count(*) from candidates c join results r on r.candidate_id = c.id "
        + "where c.session_id = ? group by r.decision order by r.decision", (RowCallbackHandler) rs -> {
          dist.put(rs.getString(1), rs.getLong(2));
        }, s.id());
    List<Map<String, Object>> last = jdbc.query(
        IMPORT_COLS + " where session_id = ? order by created_at desc limit 1", (rs, i) -> importMap(rs), s.id());
    return M.of("candidates", total, "decisions", dist, "lastImport", last.isEmpty() ? null : last.get(0));
  }

  @GetMapping("/audit")
  public List<Map<String, Object>> auditLog(@RequestAttribute("auth") AuthUser u) {
    requireAdmin(u);
    return jdbc.query("select a.id, a.at, u.email, a.action, a.target, a.details::text as details from audit_log a "
        + "left join users u on u.id = a.user_id order by a.id desc limit 200",
        (rs, i) -> M.of("id", rs.getLong("id"), "at", SessionService.ts(rs, "at"), "user", rs.getString("email"),
            "action", rs.getString("action"), "target", rs.getString("target"), "details", rs.getString("details")));
  }

  // ---------- Utilitaires ----------

  private SessionService.Row session(String code) {
    return sessions.find(code).orElseThrow(AdminController::notFound);
  }

  private static ApiException notFound() {
    return new ApiException(404, "not_found", "Ressource introuvable.");
  }

  private static void requireAdmin(AuthUser u) {
    if (!u.isAdmin()) throw new ApiException(403, "forbidden", "Reserve au responsable.");
  }

  private static OffsetDateTime parse(String iso) {
    try {
      return OffsetDateTime.parse(iso);
    } catch (DateTimeParseException e) {
      try {
        return Instant.parse(iso).atOffset(java.time.ZoneOffset.UTC);
      } catch (DateTimeParseException e2) {
        throw new ApiException(422, "validation_error", "Date invalide (format ISO 8601 attendu).");
      }
    }
  }
}
