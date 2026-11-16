package cm.proclama.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Validation d'une ligne du CSV d'import (RG5). */
public final class CsvValidator {
  private CsvValidator() {}

  public record Row(String matricule, String fullName, LocalDate dob, BigDecimal average, String decision, String mention) {}

  public record Err(String field, String message) {}

  public record Result(Row row, List<Err> errors) {}

  private static final Pattern MAT = Pattern.compile("^[A-Za-z0-9-]{4,20}$");
  private static final Pattern AVG = Pattern.compile("^\\d{1,2}(\\.\\d{1,2})?$");
  private static final Set<String> DECISIONS = Set.of("ADMIS", "AJOURNE", "ABSENT");
  private static final Set<String> MENTIONS = Set.of("PASSABLE", "ASSEZ BIEN", "BIEN", "TRES BIEN");

  public static boolean validMatricule(String m) {
    return m != null && MAT.matcher(m).matches();
  }

  public static Result validate(String mat, String nom, String dob, String avg, String dec, String men, LocalDate today) {
    mat = n(mat); nom = n(nom); dob = n(dob); avg = n(avg); dec = n(dec); men = n(men);
    List<Err> errs = new ArrayList<>();
    if (!MAT.matcher(mat).matches()) errs.add(new Err("matricule", "4 a 20 caracteres parmi lettres, chiffres et tirets"));
    if (nom.isEmpty() || nom.length() > 200) errs.add(new Err("nom_complet", "obligatoire, 200 caracteres au plus"));
    LocalDate d = null;
    try {
      d = LocalDate.parse(dob);
      if (!d.isBefore(today)) errs.add(new Err("date_naissance", "doit etre une date passee"));
    } catch (DateTimeParseException e) {
      errs.add(new Err("date_naissance", "date reelle au format AAAA-MM-JJ attendue"));
    }
    if (!DECISIONS.contains(dec)) errs.add(new Err("decision", "ADMIS, AJOURNE ou ABSENT attendu"));
    BigDecimal a = null;
    if (avg.isEmpty()) {
      if (!"ABSENT".equals(dec)) errs.add(new Err("moyenne", "obligatoire sauf pour la decision ABSENT"));
    } else if (!AVG.matcher(avg).matches() || new BigDecimal(avg).compareTo(BigDecimal.valueOf(20)) > 0) {
      errs.add(new Err("moyenne", "nombre entre 0 et 20 avec deux decimales au plus"));
    } else {
      a = new BigDecimal(avg);
    }
    if (!men.isEmpty()) {
      if (!MENTIONS.contains(men)) errs.add(new Err("mention", "PASSABLE, ASSEZ BIEN, BIEN ou TRES BIEN attendu"));
      else if (!"ADMIS".equals(dec)) errs.add(new Err("mention", "autorisee seulement si la decision est ADMIS"));
    }
    if (!errs.isEmpty()) return new Result(null, errs);
    return new Result(new Row(mat, nom, d, a, dec, men.isEmpty() ? null : men), List.of());
  }

  private static String n(String s) {
    return s == null ? "" : s.trim();
  }
}
