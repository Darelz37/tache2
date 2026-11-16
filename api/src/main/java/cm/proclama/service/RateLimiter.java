package cm.proclama.service;

import cm.proclama.config.AppProps;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Fenêtre glissante d'une minute par adresse, en mémoire de l'instance (limite effective = N x valeur). */
@Component
public class RateLimiter {
  private final Map<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();
  private final int limit;

  @Autowired
  public RateLimiter(AppProps p) {
    this(p.rateLimitPerMin());
  }

  RateLimiter(int limit) {
    this.limit = limit;
  }

  /** Retourne 0 si la requête est autorisée, sinon le nombre de secondes d'attente. */
  public int check(String ip) {
    return check(ip, System.currentTimeMillis());
  }

  int check(String ip, long now) {
    if (limit <= 0) return 0;
    if (hits.size() > 20000) {
      hits.entrySet().removeIf(e -> {
        synchronized (e.getValue()) {
          return e.getValue().isEmpty() || now - e.getValue().peekLast() >= 60000;
        }
      });
    }
    ArrayDeque<Long> q = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && now - q.peekFirst() >= 60000) q.pollFirst();
      if (q.size() >= limit) return (int) Math.max(1, (60000 - (now - q.peekFirst()) + 999) / 1000);
      q.addLast(now);
      return 0;
    }
  }
}
