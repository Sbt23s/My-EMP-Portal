package com.pixous.hrportal.modules.appreciation;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * The appreciation letter as a PDF, for the copy that goes out by email.
 *
 * <p>The portal renders the same letter as HTML and prints it through the
 * browser, which is the right tool there -- it needs no library and gives
 * selectable text at A4. Email cannot print, so the attachment is built here
 * with the PDF library payroll already uses rather than a second one added for
 * this.
 *
 * <p>Deliberately plain: A4, one column, the same words in the same order as
 * the page. A letter that looks different depending on where it was opened is
 * a letter people stop trusting.
 */
@Slf4j
@Service
public class AppreciationPdfService {

    /** The brand purple, matching the letterhead on screen. */
    private static final Color BRAND = new Color(0x4F, 0x39, 0xC7);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] render(AppreciationLetter letter, String employeeName, String designation,
                         String issuerName, String issuerRole) {
        Document doc = new Document(PageSize.A4, 56, 56, 48, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, BRAND);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BRAND);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, Color.DARK_GRAY);
            Font bodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, Color.DARK_GRAY);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.GRAY);

            Paragraph official = new Paragraph("OFFICIAL COMMUNICATION", small);
            official.setAlignment(Element.ALIGN_RIGHT);
            doc.add(official);

            Paragraph brand = new Paragraph("PIXOUS TECHNOLOGIES", brandFont);
            brand.setSpacingBefore(6);
            brand.setSpacingAfter(14);
            doc.add(brand);

            Paragraph title = new Paragraph("APPRECIATION LETTER", titleFont);
            title.setSpacingAfter(16);
            doc.add(title);

            doc.add(line("Date: " + (letter.getLetterDate() == null
                    ? "-" : letter.getLetterDate().format(DATE)), body, 14));

            doc.add(line("To,", body, 0));
            doc.add(line(employeeName, bodyBold, 0));
            if (designation != null && !designation.isBlank()) doc.add(line(designation, body, 0));
            doc.add(line("Pixous Technologies", body, 16));

            doc.add(line("Subject: Appreciation for Your Valuable Contribution", bodyBold, 16));

            doc.add(line("Dear " + employeeName + ",", body, 12));

            // The message as it was written, paragraph by paragraph.
            for (String para : (letter.getMessage() == null ? "" : letter.getMessage()).split("\n\n")) {
                if (!para.isBlank()) doc.add(line(para.trim(), body, 10));
            }

            doc.add(line("Your contribution to " + letter.getAchievement()
                    + " is highly appreciated, and we encourage you to continue maintaining "
                    + "the same level of dedication and excellence in your future endeavours.",
                    body, 10));
            doc.add(line("We are proud to have you as a part of the Pixous Technologies team "
                    + "and look forward to seeing you achieve many more milestones with us.",
                    body, 10));
            doc.add(line("Congratulations and keep up the excellent work!", bodyBold, 26));

            doc.add(line("Sincerely,", body, 26));
            doc.add(line(issuerName == null ? "-" : issuerName, bodyBold, 0));
            if (issuerRole != null && !issuerRole.isBlank()) doc.add(line(issuerRole, body, 0));
            doc.add(line("Pixous Technologies", body, 24));

            Paragraph footer = new Paragraph(
                    "This letter was issued by Pixous Technologies. Reference "
                            + letter.getReferenceCode() + ".", small);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            /*
             * The letter is already saved and the employee already notified in
             * the portal, so a PDF that will not render must not undo either --
             * the caller sends the email without the attachment.
             */
            log.warn("Could not render appreciation PDF for {}: {}",
                    letter.getReferenceCode(), e.getMessage());
            return null;
        }
    }

    private static Paragraph line(String text, Font font, float spacingAfter) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingAfter(spacingAfter);
        return p;
    }
}
