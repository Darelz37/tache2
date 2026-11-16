package cm.proclama.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ServiceTests {
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

  @Test
  void ligneValide() {
    var r = CsvValidator.validate("BTS26-000123", "Jean EXEMPLE", "2004-03-17", "13.52", "ADMIS", "ASSEZ BIEN", TODAY);
    assertTrue(r.errors().isEmpty());
    assertEquals("ADMIS", r.row().decision());
  }

  @Test
  void ligneInvalide() {
    assertFalse(CsvValidator.validate("BTS26-000124", "Marie", "2003-02-30", "8.75", "AJOURNE", "", TODAY).errors().isEmpty());
    assertFalse(CsvValidator.validate("BTS26-000124", "Marie", "2003-02-10", "8.75", "AJOURNE", "BIEN", TODAY).errors().isEmpty());
    assertFalse(CsvValidator.validate("BTS26-000124", "Marie", "2003-02-10", "20.5", "ADMIS", "", TODAY).errors().isEmpty());
    assertFalse(CsvValidator.validate("AB", "Marie", "2003-02-10", "10", "ADMIS", "", TODAY).errors().isEmpty());
  }

  @Test
  void absentSansMoyenne() {
    var r = CsvValidator.validate("BTS26-000125", "Paul ESSAI", "2004-06-30", "", "ABSENT", "", TODAY);
    assertTrue(r.errors().isEmpty());
    assertNull(r.row().average());
  }

  @Test
  void statutEffectif() {
    Instant h = Instant.parse("2026-11-10T12:00:00Z");
    assertEquals("draft", SessionStatus.effective("draft", null, h));
    assertEquals("scheduled", SessionStatus.effective("scheduled", h, h.minusSeconds(1)));
    assertEquals("published", SessionStatus.effective("scheduled", h, h));
    assertEquals("published", SessionStatus.effective("published", null, h));
    assertEquals("unpublished", SessionStatus.effective("unpublished", h, h.plusSeconds(5)));
  }

  @Test
  void limiteDeDebit() {
    RateLimiter l = new RateLimiter(3);
    long t = 1_000_000L;
    assertEquals(0, l.check("a", t));
    assertEquals(0, l.check("a", t + 1));
    assertEquals(0, l.check("a", t + 2));
    assertTrue(l.check("a", t + 3) > 0);
    assertEquals(0, l.check("b", t + 3));
    assertEquals(0, l.check("a", t + 61_000)); // la fenêtre a glissé
    assertEquals(0, new RateLimiter(0).check("x", t)); // 0 désactive
  }
}
