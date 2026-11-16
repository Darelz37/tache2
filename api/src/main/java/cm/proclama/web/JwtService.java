package cm.proclama.web;

import cm.proclama.config.AppProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Jetons signés HS256, durée de 8 heures. */
@Component
public class JwtService {
  private static final long TTL_SECONDS = 8 * 3600L;
  private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
  private final byte[] key;
  private final ObjectMapper om;

  public JwtService(AppProps p, ObjectMapper om) {
    this.key = p.jwtSecret().getBytes(StandardCharsets.UTF_8);
    this.om = om;
  }

  public String sign(AuthUser u) {
    try {
      String head = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
      Map<String, Object> claims = M.of("sub", u.id().toString(), "email", u.email(), "name", u.name(), "role", u.role(),
          "exp", Instant.now().getEpochSecond() + TTL_SECONDS);
      String body = ENC.encodeToString(om.writeValueAsBytes(claims));
      return head + "." + body + "." + ENC.encodeToString(hmac(head + "." + body));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Retourne l'utilisateur, ou null si le jeton est invalide ou expiré. */
  @SuppressWarnings("unchecked")
  public AuthUser verify(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) return null;
      byte[] expected = hmac(parts[0] + "." + parts[1]);
      if (!MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(parts[2]))) return null;
      Map<String, Object> c = om.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
      if (((Number) c.get("exp")).longValue() < Instant.now().getEpochSecond()) return null;
      return new AuthUser(UUID.fromString((String) c.get("sub")), (String) c.get("email"), (String) c.get("name"),
          (String) c.get("role"));
    } catch (Exception e) {
      return null;
    }
  }

  private byte[] hmac(String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
  }
}
