package cm.proclama.service;

import java.time.Instant;

/** Miroir Java du calcul SQL du statut effectif (la source de vérité reste la requête SQL, horloge de la base). */
public final class SessionStatus {
  private SessionStatus() {}

  public static String effective(String status, Instant publishAt, Instant now) {
    if ("scheduled".equals(status) && publishAt != null && !publishAt.isAfter(now)) return "published";
    return status;
  }
}
