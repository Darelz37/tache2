package cm.proclama.config;

import jakarta.annotation.PostConstruct;
import java.sql.*;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Migrations au démarrage sous verrou consultatif + création de l'administrateur initial. */
@Component
public class Migrator {
  private static final Logger log = LoggerFactory.getLogger(Migrator.class);
  private static final long LOCK = 727274L;
  private final DataSource ds;
  private final AppProps p;

  public Migrator(DataSource ds, AppProps p) {
    this.ds = ds;
    this.p = p;
  }

  @PostConstruct
  public void migrate() throws SQLException {
    try (Connection c = ds.getConnection()) {
      exec(c, "select pg_advisory_lock(" + LOCK + ")");
      try {
        ScriptUtils.executeSqlScript(c, new ClassPathResource("db/schema.sql"));
        exec(c, "insert into schema_version(version) values (1) on conflict do nothing");
        bootstrap(c);
      } finally {
        exec(c, "select pg_advisory_unlock(" + LOCK + ")");
      }
    }
    log.info("migrations appliquees");
  }

  private void bootstrap(Connection c) throws SQLException {
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("select count(*) from users")) {
      rs.next();
      if (rs.getLong(1) > 0) return;
    }
    if (p.bootstrapAdminPassword() == null || p.bootstrapAdminPassword().isBlank()) {
      log.warn("BOOTSTRAP_ADMIN_PASSWORD absent : aucun administrateur cree");
      return;
    }
    try (PreparedStatement ps = c.prepareStatement(
        "insert into users(email, name, password_hash, role) values (?, ?, ?, 'admin') on conflict (email) do nothing")) {
      ps.setString(1, p.bootstrapAdminEmail());
      ps.setString(2, "Administrateur");
      ps.setString(3, new BCryptPasswordEncoder().encode(p.bootstrapAdminPassword()));
      ps.executeUpdate();
      log.info("administrateur initial cree");
    }
  }

  private static void exec(Connection c, String sql) throws SQLException {
    try (Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }
}
