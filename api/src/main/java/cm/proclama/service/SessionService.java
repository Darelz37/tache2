package cm.proclama.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
  public record Row(UUID id, String code, String title, String status, String effective, Instant publishAt, Instant serverTime) {}

  public static final String EFFECTIVE =
      "case when status = 'scheduled' and publish_at <= now() then 'published' else status end";

  private final JdbcTemplate jdbc;

  public SessionService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Une seule requête, avec l'horloge de la base. */
  public Optional<Row> find(String code) {
    return jdbc.query(
            "select id, code, title, status, " + EFFECTIVE + " as effective_status, publish_at, now() as server_time "
                + "from sessions where code = ?",
            (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("title"),
                rs.getString("status"), rs.getString("effective_status"), ts(rs, "publish_at"),
                ts(rs, "server_time").truncatedTo(ChronoUnit.SECONDS)),
            code)
        .stream().findFirst();
  }

  public static Instant ts(ResultSet rs, String col) throws SQLException {
    OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
    return o == null ? null : o.toInstant();
  }
}
