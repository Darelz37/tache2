package cm.proclama.service;

import cm.proclama.config.AppProps;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Worker d'import (même image que l'API, lancé avec --app.mode=worker).
 * Réservation atomique (FOR UPDATE SKIP LOCKED), lots de 1 000, battement de coeur, reprise des imports orphelins.
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "worker")
public class ImportWorker {
  private static final Logger log = LoggerFactory.getLogger(ImportWorker.class);
  private static final int BATCH = 1000;
  private static final int MAX_ROWS = 50_000;
  private static final int MAX_REPORT = 1000;
  private static final List<String> HEADERS = List.of("matricule", "nom_complet", "date_naissance", "moyenne", "decision", "mention");

  private static final String RESERVE =
      "update imports set status = 'processing', started_at = now(), heartbeat_at = now(), rows_total = 0, rows_ok = 0, "
          + "rows_error = 0, errors_truncated = false, error_key = null "
          + "where id = (select id from imports where status = 'pending' order by created_at for update skip locked limit 1) "
          + "returning id, session_id, object_key";

  private static final String UPSERT =
      "with src as (select * from unnest(?::text[], ?::text[], ?::date[], ?::text[], ?::numeric[], ?::text[]) "
          + "as t(matricule, full_name, dob, decision, average, mention)), "
          + "c as (insert into candidates(session_id, matricule, full_name, dob) select ?::uuid, matricule, full_name, dob from src "
          + "on conflict (session_id, matricule) do update set full_name = excluded.full_name, dob = excluded.dob "
          + "returning id, matricule) "
          + "insert into results(candidate_id, decision, average, mention, updated_at) "
          + "select c.id, s.decision, s.average, s.mention, now() from c join src s on s.matricule = c.matricule "
          + "on conflict (candidate_id) do update set decision = excluded.decision, average = excluded.average, "
          + "mention = excluded.mention, updated_at = now()";

  private final JdbcTemplate jdbc;
  private final S3Service s3;
  private final AppProps p;
  private ScheduledExecutorService exec;

  public ImportWorker(JdbcTemplate jdbc, S3Service s3, AppProps p) {
    this.jdbc = jdbc;
    this.s3 = s3;
    this.p = p;
  }

  @PostConstruct
  void start() {
    exec = Executors.newSingleThreadScheduledExecutor();
    exec.scheduleWithFixedDelay(this::cycle, 1, Math.max(1, p.workerIntervalSeconds()), TimeUnit.SECONDS);
    log.info("worker demarre");
  }

  @PreDestroy
  void stop() {
    exec.shutdown();
  }

  void cycle() {
    MDC.put("instance", p.instanceName());
    try {
      touch();
      int requeued = jdbc.update(
          "update imports set status = 'pending' where status = 'processing' and heartbeat_at < now() - interval '60 seconds'");
      if (requeued > 0) log.warn("imports orphelins remis en attente : " + requeued);
      while (true) {
        List<Map<String, Object>> rows = jdbc.queryForList(RESERVE);
        if (rows.isEmpty()) break;
        process(rows.get(0));
      }
    } catch (Exception e) {
      log.error("cycle du worker en echec", e);
    } finally {
      MDC.clear();
    }
  }

  private void touch() {
    jdbc.update("insert into worker_status(name, last_run) values ('import-worker', now()) "
        + "on conflict (name) do update set last_run = now()");
  }

  /** Rapport d'erreurs : au plus MAX_REPORT entrées détaillées. */
  private static final class Report {
    final List<String[]> entries = new ArrayList<>();
    boolean truncated = false;

    void add(int line, String field, String msg) {
      if (entries.size() >= MAX_REPORT) {
        truncated = true;
        return;
      }
      entries.add(new String[] {String.valueOf(line), field, msg});
    }

    String toCsv() {
      StringBuilder sb = new StringBuilder("\uFEFFligne;champ;message\n");
      for (String[] e : entries) sb.append(e[0]).append(';').append(esc(e[1])).append(';').append(esc(e[2])).append('\n');
      if (truncated) sb.append("0;rapport;tronque a ").append(MAX_REPORT).append(" entrees\n");
      return sb.toString();
    }

    private static String esc(String s) {
      return (s.contains(";") || s.contains("\"") || s.contains("\n")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
  }

  private void process(Map<String, Object> row) {
    UUID id = (UUID) row.get("id");
    UUID sid = (UUID) row.get("session_id");
    String key = (String) row.get("object_key");
    String code = jdbc.queryForObject("select code from sessions where id = ?", String.class, sid);
    Report rep = new Report();
    int total = 0, ok = 0, err = 0;
    boolean failed = false;
    log.info("import demarre " + id);
    try (InputStream in = s3.getStream(key);
        BufferedReader rd = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      rd.mark(1);
      if (rd.read() != 0xFEFF) rd.reset(); // saute un éventuel BOM UTF-8
      CSVFormat fmt = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true)
          .setIgnoreEmptyLines(true).setTrim(true).build();
      try (CSVParser parser = fmt.parse(rd)) {
        if (!parser.getHeaderMap().keySet().containsAll(HEADERS)) {
          rep.add(1, "en-tete", "colonnes attendues : " + String.join(";", HEADERS));
          throw new IllegalStateException("en-tete invalide");
        }
        LocalDate today = LocalDate.now();
        Map<String, Integer> lastLine = new HashMap<>();
        Map<String, CsvValidator.Row> batch = new LinkedHashMap<>();
        for (CSVRecord rec : parser) {
          int line = (int) parser.getCurrentLineNumber();
          total++;
          if (total > MAX_ROWS) {
            rep.add(line, "fichier", "plus de " + MAX_ROWS + " lignes de donnees");
            failed = true;
            break;
          }
          if (rec.size() != HEADERS.size()) {
            err++;
            rep.add(line, "ligne", "6 colonnes attendues, " + rec.size() + " trouvees");
            continue;
          }
          CsvValidator.Result r = CsvValidator.validate(rec.get("matricule"), rec.get("nom_complet"),
              rec.get("date_naissance"), rec.get("moyenne"), rec.get("decision"), rec.get("mention"), today);
          if (!r.errors().isEmpty()) {
            err++;
            for (CsvValidator.Err e : r.errors()) rep.add(line, e.field(), e.message());
            continue;
          }
          Integer prev = lastLine.put(r.row().matricule(), line); // RG6 : la dernière ligne l'emporte
          if (prev != null) rep.add(prev, "matricule", "AVERTISSEMENT : doublon, la ligne " + line + " l'emporte");
          batch.put(r.row().matricule(), r.row());
          ok++;
          if (batch.size() >= BATCH) {
            upsert(sid, batch.values());
            batch.clear();
            progress(id, total, ok, err, rep.truncated);
          }
        }
        if (!failed && !batch.isEmpty()) upsert(sid, batch.values());
      }
    } catch (Exception e) {
      log.error("import en echec " + id, e);
      failed = true;
      if (rep.entries.isEmpty()) rep.add(0, "fichier", "erreur de traitement : " + e.getMessage());
    }
    String errKey = "errors/" + code + "/" + id + ".csv";
    s3.putBytes(errKey, rep.toCsv().getBytes(StandardCharsets.UTF_8), "text/csv");
    jdbc.update("update imports set status = ?, rows_total = ?, rows_ok = ?, rows_error = ?, errors_truncated = ?, "
        + "error_key = ?, finished_at = now(), heartbeat_at = now() where id = ?",
        failed ? "failed" : "done", total, ok, err, rep.truncated, errKey, id);
    log.info("import termine " + id + " : " + ok + " valides, " + err + " erreurs");
  }

  private void progress(UUID id, int total, int ok, int err, boolean truncated) {
    jdbc.update("update imports set rows_total = ?, rows_ok = ?, rows_error = ?, errors_truncated = ?, heartbeat_at = now() where id = ?",
        total, ok, err, truncated, id);
    touch();
  }

  /** Insertion avec mise à jour en cas de conflit : rejouer un lot ne crée aucun doublon. */
  private void upsert(UUID sessionId, Collection<CsvValidator.Row> rows) {
    int n = rows.size();
    Object[] mat = new Object[n], name = new Object[n], dob = new Object[n], avg = new Object[n], dec = new Object[n], men = new Object[n];
    int i = 0;
    for (CsvValidator.Row r : rows) {
      mat[i] = r.matricule();
      name[i] = r.fullName();
      dob[i] = java.sql.Date.valueOf(r.dob());
      avg[i] = r.average();
      dec[i] = r.decision();
      men[i] = r.mention();
      i++;
    }
    jdbc.execute((ConnectionCallback<Void>) con -> {
      try (PreparedStatement ps = con.prepareStatement(UPSERT)) {
        ps.setArray(1, con.createArrayOf("text", mat));
        ps.setArray(2, con.createArrayOf("text", name));
        ps.setArray(3, con.createArrayOf("date", dob));
        ps.setArray(4, con.createArrayOf("text", dec));
        ps.setArray(5, con.createArrayOf("numeric", avg));
        ps.setArray(6, con.createArrayOf("text", men));
        ps.setObject(7, sessionId);
        ps.executeUpdate();
      }
      return null;
    });
  }
}
