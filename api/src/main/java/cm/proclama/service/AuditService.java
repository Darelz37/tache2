package cm.proclama.service;

import cm.proclama.web.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper om;

  public AuditService(JdbcTemplate jdbc, ObjectMapper om) {
    this.jdbc = jdbc;
    this.om = om;
  }

  public void log(AuthUser u, String action, String target, Map<String, Object> details) {
    String json;
    try {
      json = om.writeValueAsString(details == null ? Map.of() : details);
    } catch (Exception e) {
      json = "{}";
    }
    jdbc.update("insert into audit_log(user_id, action, target, details) values (?, ?, ?, ?::jsonb)",
        u == null ? null : u.id(), action, target, json);
  }
}
