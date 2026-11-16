package cm.proclama.web;

import cm.proclama.config.AppProps;
import cm.proclama.service.Counters;
import cm.proclama.service.S3Service;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {
  private final JdbcTemplate jdbc;
  private final S3Service s3;
  private final Counters counters;
  private final AppProps p;

  public SystemController(JdbcTemplate jdbc, S3Service s3, Counters counters, AppProps p) {
    this.jdbc = jdbc;
    this.s3 = s3;
    this.counters = counters;
    this.p = p;
  }

  /** Vivacité : ne touche à aucune dépendance. */
  @GetMapping("/health")
  public Map<String, Object> health() {
    return M.of("status", "UP");
  }

  @GetMapping("/ready")
  public Map<String, Object> ready() {
    String db = dbState();
    String st = s3State();
    if (!db.equals("ok") || !st.equals("ok")) {
      throw new ApiException(503, "not_ready", "Dependances indisponibles.", M.of("db", db, "s3", st), Map.of());
    }
    return M.of("status", "READY", "db", db, "s3", st);
  }

  @GetMapping("/whoami")
  public Map<String, Object> whoami() {
    return M.of("instance", p.instanceName(), "version", p.appVersion(), "startedAt", counters.startedAt,
        "db", dbState(), "s3", s3State(), "workerLastRunSeconds", workerAge(),
        "counters", M.of("requests", counters.requests.get(), "lookups", counters.lookups.get(),
            "statuses", counters.statuses.get()));
  }

  private String dbState() {
    try {
      jdbc.queryForObject("select 1", Integer.class);
      return "ok";
    } catch (Exception e) {
      return "down";
    }
  }

  private String s3State() {
    try {
      s3.ping();
      return "ok";
    } catch (Exception e) {
      return "down";
    }
  }

  private Long workerAge() {
    try {
      List<Long> r = jdbc.queryForList(
          "select extract(epoch from now() - last_run)::bigint from worker_status where name = 'import-worker'", Long.class);
      return r.isEmpty() ? null : r.get(0);
    } catch (Exception e) {
      return null;
    }
  }
}
