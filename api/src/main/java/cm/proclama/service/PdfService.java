package cm.proclama.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** Attestation d'une page (réussite pour ADMIS, participation sinon). */
@Service
public class PdfService {
  public byte[] build(Found f) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document d = new Document(PageSize.A4, 64, 64, 80, 64);
      PdfWriter.getInstance(d, out);
      d.open();
      boolean success = "ADMIS".equals(f.decision());
      Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22);
      Font b = FontFactory.getFont(FontFactory.HELVETICA, 12);
      Font s = FontFactory.getFont(FontFactory.HELVETICA, 9);
      Paragraph t = new Paragraph(success ? "ATTESTATION DE REUSSITE" : "ATTESTATION DE PARTICIPATION", h);
      t.setAlignment(Element.ALIGN_CENTER);
      t.setSpacingAfter(36);
      d.add(t);
      d.add(line("Session : " + f.sessionTitle() + " (" + f.sessionCode() + ")", b));
      d.add(line("Il est certifie que :", b));
      d.add(line(f.fullName(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
      d.add(line("ne(e) le " + f.dob() + ", matricule " + f.matricule() + ",", b));
      d.add(line(success ? "a ete declare(e) admis(e) a la session." : "a participe a la session.", b));
      d.add(line("Decision : " + f.decision(), b));
      d.add(line("Moyenne : " + (f.average() == null ? "-" : f.average().toPlainString() + " / 20"), b));
      d.add(line("Mention : " + (f.mention() == null ? "-" : f.mention()), b));
      d.add(line("Document etabli le " + LocalDate.now() + ".", b));
      d.add(line("Empreinte de verification : " + fingerprint(f), s));
      d.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Paragraph line(String text, Font f) {
    Paragraph p = new Paragraph(text, f);
    p.setSpacingAfter(12);
    return p;
  }

  static String fingerprint(Found f) {
    try {
      String raw = f.sessionCode() + "|" + f.matricule() + "|" + f.decision() + "|" + f.average() + "|" + f.mention();
      byte[] h = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(h).substring(0, 16).toUpperCase();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
