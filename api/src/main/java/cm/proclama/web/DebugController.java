package cm.proclama.web;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Points de diagnostic : n'existent (404) que si ENABLE_DEBUG=true. */
@RestController
@RequestMapping("/api/debug")
@ConditionalOnProperty(name = "app.enable-debug", havingValue = "true")
public class DebugController {
  private static final List<ByteBuffer> HELD = new ArrayList<>();

  /** Occupe le CPU sur le fil de la requête : sous quota, c'est ce qui sature l'instance. */
  @GetMapping("/cpu")
  public Map<String, Object> cpu(@RequestParam(defaultValue = "10") int seconds) {
    check(seconds, 1, 60, "seconds");
    long end = System.nanoTime() + seconds * 1_000_000_000L;
    double x = 1;
    while (System.nanoTime() < end) x = Math.sqrt(x + 1) * Math.sin(x) + Math.cos(x) + 2;
    return M.of("cpuSeconds", seconds, "checksum", x);
  }

  /** Alloue hors du tas de la JVM : au-delà du quota du conteneur, le noyau arrête le processus (code 137). */
  @GetMapping("/mem")
  public Map<String, Object> mem(@RequestParam(defaultValue = "64") int mb) {
    check(mb, 1, 2048, "mb");
    synchronized (HELD) {
      for (int i = 0; i < mb; i++) HELD.add(ByteBuffer.allocateDirect(1 << 20));
      return M.of("allocatedMb", mb, "heldMb", HELD.size());
    }
  }

  @GetMapping("/crash")
  public Map<String, Object> crash() {
    new Thread(() -> {
      try {
        Thread.sleep(200);
      } catch (InterruptedException ignored) {
      }
      Runtime.getRuntime().halt(1);
    }).start();
    return M.of("crashing", true);
  }

  private static void check(int v, int min, int max, String name) {
    if (v < min || v > max) throw new ApiException(422, "validation_error", name + " doit etre entre " + min + " et " + max + ".");
  }
}
