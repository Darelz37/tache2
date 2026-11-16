package cm.proclama.web;

import java.util.LinkedHashMap;
import java.util.Map;

/** Construit une Map ordonnée qui accepte les valeurs nulles (Map.of les refuse). */
public final class M {
  private M() {}

  public static Map<String, Object> of(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }
}
