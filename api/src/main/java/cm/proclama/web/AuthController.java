package cm.proclama.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  public record LoginReq(String email, String password) {}

  private final JdbcTemplate jdbc;
  private final JwtService jwt;
  private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

  public AuthController(JdbcTemplate jdbc, JwtService jwt) {
    this.jdbc = jdbc;
    this.jwt = jwt;
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@RequestBody(required = false) LoginReq r) {
    ApiException bad = new ApiException(401, "unauthorized", "Identifiants invalides.");
    if (r == null || r.email() == null || r.password() == null || r.email().length() > 200) throw bad;
    List<Object[]> rows = jdbc.query(
        "select id, email, name, password_hash, role from users where lower(email) = lower(?)",
        (rs, i) -> new Object[] {rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("name"),
            rs.getString("password_hash"), rs.getString("role")},
        r.email().trim());
    if (rows.isEmpty() || !enc.matches(r.password(), (String) rows.get(0)[3])) throw bad;
    Object[] u = rows.get(0);
    AuthUser user = new AuthUser((UUID) u[0], (String) u[1], (String) u[2], (String) u[4]);
    return M.of("token", jwt.sign(user), "user", userMap(user));
  }

  @GetMapping("/me")
  public Map<String, Object> me(@RequestAttribute("auth") AuthUser u) {
    return userMap(u);
  }

  private static Map<String, Object> userMap(AuthUser u) {
    return M.of("id", u.id(), "email", u.email(), "name", u.name(), "role", u.role());
  }
}
