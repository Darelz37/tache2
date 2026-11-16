package cm.proclama.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Résultat d'une consultation réussie. */
public record Found(String sessionCode, String sessionTitle, UUID candidateId, String matricule, String fullName,
    LocalDate dob, String decision, BigDecimal average, String mention) {}
