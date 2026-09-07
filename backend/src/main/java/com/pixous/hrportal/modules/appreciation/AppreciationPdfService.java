package com.pixous.hrportal.modules.appreciation;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    /**
     * The company mark, from the classpath copy the payslip already ships.
     *
     * <p>The letter shows the logo on screen and the downloaded PDF did not,
     * so the employee's own copy -- the one they keep, forward, or attach to a
     * visa application -- was the only version without the company's mark on
     * it, and looked less official than the page they were reading.
     *
     * <p>Deliberately the same file the payslip embeds rather than a new asset
     * beside it: it is byte-for-byte the image the page loads from
     * web/public, so the letter on screen and the letter in the PDF cannot
     * drift apart, and there is one file to replace when the logo changes.
     *
     * <p>Read once into memory. It is ~400KB and a letter is rendered per
     * download, so re-reading it from the jar each time would be wasted work.
     */
    private static final byte[] LOGO_BYTES = loadResource("/payslip/pixous-logo.png");

    /** Null rather than an exception: a missing logo must not cost the letter. */
    private static byte[] loadResource(String path) {
        try (InputStream in = AppreciationPdfService.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

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

            /*
             * The letterhead, laid out as it is on screen: the mark on the
             * left, "OFFICIAL COMMUNICATION" on the right, both on one line.
             *
             * A table rather than two paragraphs because a paragraph would put
             * them one above the other, and the page puts them side by side --
             * the two versions of this letter have to look like the same
             * document.
             */
            PdfPTable head = new PdfPTable(2);
            head.setWidthPercentage(100);
            head.setWidths(new float[]{1f, 2f});

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setPadding(0);
            logoCell.setVerticalAlignment(Element.ALIGN_TOP);
            if (LOGO_BYTES != null) {
                try {
                    Image img = Image.getInstance(LOGO_BYTES);
                    // Bounded, not stretched: the source is 2705x1494, and
                    // scaleToFit keeps its proportions inside the box.
                    img.scaleToFit(96, 46);
                    logoCell.addElement(img);
                } catch (Exception ignored) {
                    // An unreadable logo leaves an empty cell. The letter is
                    // the point; the mark is decoration on top of it.
                }
            }
            head.addCell(logoCell);

            PdfPCell officialCell = new PdfPCell();
            officialCell.setBorder(Rectangle.NO_BORDER);
            officialCell.setPadding(0);
            officialCell.setVerticalAlignment(Element.ALIGN_TOP);
            Paragraph official = new Paragraph("OFFICIAL COMMUNICATION", small);
            official.setAlignment(Element.ALIGN_RIGHT);
            officialCell.addElement(official);
            head.addCell(officialCell);

            doc.add(head);

            /*
             * The company name, with the purple rule under it that the page
             * draws as a border. Without it the PDF had the logo but not the
             * line, so the two versions of the letterhead still did not match.
             *
             * Drawn as a cell's bottom border rather than a graphic, so it
             * spans the text column and moves with the layout instead of being
             * pinned to a coordinate.
             */
            PdfPTable rule = new PdfPTable(1);
            rule.setWidthPercentage(100);
            rule.setSpacingBefore(6);
            rule.setSpacingAfter(14);
            PdfPCell brandCell = new PdfPCell(new Paragraph("PIXOUS TECHNOLOGIES", brandFont));
            brandCell.setBorder(Rectangle.BOTTOM);
            brandCell.setBorderColor(BRAND);
            brandCell.setBorderWidthBottom(1.5f);
            brandCell.setPadding(0);
            brandCell.setPaddingBottom(5);
            rule.addCell(brandCell);
            doc.add(rule);

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
