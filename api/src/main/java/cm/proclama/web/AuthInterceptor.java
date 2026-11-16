package cm.proclama.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Exige un jeton valide sur /api/admin/** et /api/me. Le rôle admin est contrôlé dans les routes concernées. */
@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final JwtService jwt;

  public AuthInterceptor(JwtService jwt) {
    this.jwt = jwt;
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
    String h = req.getHeader("Authorization");
    AuthUser u = (h != null && h.startsWith("Bearer ")) ? jwt.verify(h.substring(7)) : null;
    if (u == null) throw new ApiException(401, "unauthorized", "Authentification requise.");
    req.setAttribute("auth", u);
    return true;
  }
}
