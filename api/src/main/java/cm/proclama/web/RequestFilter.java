package cm.proclama.web;

import cm.proclama.config.AppProps;
import cm.proclama.service.Counters;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Identifiant de requête, compteurs d'instance et une ligne JSON par requête (format logstash de Spring Boot). */
@Component
public class RequestFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger("http");
  private final Counters counters;
  private final String instance;

  public RequestFilter(Counters counters, AppProps p) {
    this.counters = counters;
    this.instance = p.instanceName();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getRequestURI();
    boolean health = path.equals("/api/health");
    String rid = req.getHeader("X-Request-Id");
    if (rid == null || rid.isBlank() || rid.length() > 64) rid = UUID.randomUUID().toString();
    long t0 = System.nanoTime();
    res.setHeader("X-Request-Id", rid);
    res.setHeader("X-Instance", instance);
    MDC.put("requestId", rid);
    MDC.put("instance", instance);
    MDC.put("method", req.getMethod());
    MDC.put("path", path);
    try {
      chain.doFilter(req, res);
    } finally {
      if (!health) {
        counters.requests.incrementAndGet();
        MDC.put("status", String.valueOf(res.getStatus()));
        MDC.put("durationMs", String.valueOf((System.nanoTime() - t0) / 1_000_000));
        log.info("request");
      }
      MDC.clear();
    }
  }
}
