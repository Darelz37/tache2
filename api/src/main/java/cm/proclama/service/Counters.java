package cm.proclama.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Compteurs propres à l'instance (prouvent la répartition de charge). */
@Component
public class Counters {
  public final AtomicLong requests = new AtomicLong();
  public final AtomicLong lookups = new AtomicLong();
  public final AtomicLong statuses = new AtomicLong();
  public final Instant startedAt = Instant.now();
}
