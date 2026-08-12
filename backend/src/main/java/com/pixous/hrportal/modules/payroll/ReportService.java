package com.pixous.hrportal.modules.payroll;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.common.StorageService;
import com.pixous.hrportal.modules.org.DesignationRepository;
import com.pixous.hrportal.modules.org.DepartmentRepository;
import com.pixous.hrportal.modules.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates payslip PDFs with OpenPDF and stores them via {@link StorageService}.
 *
 * <p>The layout intentionally mirrors the Pixous Technologies company payslip:
 * a centered logo + company header, a two-column employee/pay meta grid, a
 * side-by-side Earnings / Deductions table, the net pay in figures and words,
 * and an employer / employee signature block.</p>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    // ---- Palette (kept understated to match the reference print) ----
    private static final Color INK = new Color(0x11, 0x18, 0x27);      // near-black text
    private static final Color MUTED = new Color(0x55, 0x5B, 0x66);    // labels
    private static final Color LINE = new Color(0xBB, 0xBB, 0xBB);     // hairlines
    private static final Color BAND = new Color(0xE9, 0xEC, 0xEF);     // total-row band

    // ---- Company defaults (used when the admin left the field blank) ----
    private static final String COMPANY_NAME = "Pixous Technologies Pvt. Ltd";
    private static final String COMPANY_ADDRESS =
            "382, Lakshmanan Nagar, 2nd St. Ext, Gandhipuram, Coimbatore – 641012";
    private static final String COMPANY_GSTIN = "33AAMCP3151E1ZO";

    // ---- Employer signatory (fixed for this company) ----
    private static final String EMPLOYER_SIGN_NAME = "Vanaraja D";
    private static final String EMPLOYER_SIGN_TITLE = "Office Administrator";
    private static final String EMPLOYER_SIGN_PHONE = "+91 70940 47000";

    // ---- Bundled assets (loaded once from the classpath) ----
    private static final byte[] LOGO_BYTES = loadResource("/payslip/pixous-logo.png");
    private static final byte[] FONT_REGULAR = loadResource("/payslip/DejaVuSans.ttf");
    private static final byte[] FONT_BOLD = loadResource("/payslip/DejaVuSans-Bold.ttf");
    private static BaseFont baseRegular;
    private static BaseFont baseBold;

    private final StorageService storageService;
    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;

    // ================================================================
    // Public entry points
    // ================================================================

    public String renderPayslipPdf(Payslip p, User user) {
        return renderPayslipPdf(p, user, user.getName(), user.getEmployeeCode());
    }

    public String renderPayslipPdf(Payslip p, User user, String displayName, String displayCode) {
        byte[] bytes = payslipPdfBytes(p, user, displayName, displayCode);
        String code = displayCode != null ? displayCode : user.getEmployeeCode();
        String filename = "payslip_" + code + "_"
                + p.getPayYear() + "_" + String.format("%02d", p.getPayMonth()) + ".pdf";
        return storageService.storeBytes(bytes, "payslips", filename);
    }

    public byte[] read(String relativePath) {
        return storageService.read(relativePath);
    }

    public byte[] payslipPdfBytes(Payslip p, User user) {
        return payslipPdfBytes(p, user, user.getName(), user.getEmployeeCode());
    }

    public byte[] payslipPdfBytes(Payslip p, User user, String displayName, String displayCode) {
        Document doc = new Document(PageSize.A4, 42, 42, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Fonts f = new Fonts();
            String rs = f.unicodeRupee ? "₹" : "Rs.";

            doc.add(buildHeader(p, f));
            doc.add(buildMetaGrid(p, user, displayName, displayCode, rs, f));
            doc.add(buildEarningsDeductions(p, rs, f));
            doc.add(buildNetInWords(p, rs, f));
            doc.add(buildSignatures(p, user, displayName, f));

            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new ApiException(ErrorCode.INTERNAL, "Failed to render payslip PDF");
        }
    }

    // ================================================================
    // Section builders
    // ================================================================

    /** Logo on the left, company name + "Payslip" + address + GSTIN centered. */
    private PdfPTable buildHeader(Payslip p, Fonts f) throws DocumentException, IOException {
        PdfPTable header = new PdfPTable(3);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.2f, 3.6f, 1.2f});

        // Left: logo
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        byte[] logo = resolveLogo(p);
        if (logo != null) {
            try {
                com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(logo);
                img.scaleToFit(100, 48);
                logoCell.addElement(img);
            } catch (Exception ignored) {
                // fall through to an empty cell
            }
        }
        header.addCell(logoCell);

        // Center: company block
        String companyName = firstNonBlank(p.getCompanyName(), COMPANY_NAME);
        String address = firstNonBlank(p.getCompanyAddress(), COMPANY_ADDRESS);
        String gstin = firstNonBlank(p.getCompanyGstin(), COMPANY_GSTIN);

        PdfPCell center = new PdfPCell();
        center.setBorder(Rectangle.NO_BORDER);
        center.setVerticalAlignment(Element.ALIGN_MIDDLE);
        center.addElement(centered(companyName, f.h1));
        center.addElement(centered("Payslip", f.h2));
        center.addElement(centered(address, f.tiny));
        center.addElement(centered("GSTIN # " + gstin, f.tiny));
        header.addCell(center);

        // Right: spacer to keep the center block page-centered
        PdfPCell spacer = new PdfPCell();
        spacer.setBorder(Rectangle.NO_BORDER);
        header.addCell(spacer);

        header.setSpacingAfter(14);
        return header;
    }

    /** Two label/value columns: identity on the left, designation/pay on the right. */
    private PdfPTable buildMetaGrid(Payslip p, User user, String displayName, String displayCode,
                                    String rs, Fonts f) throws DocumentException {
        String designation = resolveDesignation(p, user);
        String department = resolveDepartment(p, user);
        String payDate = p.getPayDate() != null
                ? p.getPayDate().format(DateTimeFormatter.ofPattern("M/d/yyyy"))
                : "-";
        String payPeriod = Month.of(p.getPayMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + ", " + p.getPayYear();
        String workingDays = p.getWorkingDays() != null ? String.valueOf(p.getWorkingDays()) : "-";
        String lopDays = p.getLopDays() != null ? p.getLopDays().stripTrailingZeros().toPlainString() : "0";

        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setWidths(new float[]{1.5f, 2f, 1.5f, 2f});

        metaRow(meta, f, "Employee ID", nz(displayCode), "Designation", designation, false);
        metaRow(meta, f, "Employee Name", nz(displayName), "Department", department, false);
        metaRow(meta, f, "Pay Date", payDate, "Pay Period", payPeriod, false);
        metaRow(meta, f, "Bank Name", nz(p.getBankName()), "Bank A/C #", nz(p.getBankAccount()), false);
        metaRow(meta, f, "No of Working Days", workingDays, "Basic Pay", money(p.getGrossSalary(), rs), true);
        metaRow(meta, f, "Loss of Pay Days", lopDays, "", "", false);

        meta.setSpacingAfter(16);
        return meta;
    }

    /** Side-by-side Earnings / Deductions with total band and a Net Pay cell. */
    private PdfPTable buildEarningsDeductions(Payslip p, String rs, Fonts f) throws DocumentException {
        List<String[]> earnings = new ArrayList<>();
        earnings.add(new String[]{"Basic Pay Adj", money(p.getBasicSalary(), rs)});
        if (isPositive(p.getHra()))
            earnings.add(new String[]{"HRA", money(p.getHra(), rs)});
        earnings.add(new String[]{"Allowances", money(p.getAllowances(), rs)});
        earnings.add(new String[]{"Expenses", money(p.getExpensesPay(), rs)});
        if (isPositive(p.getOvertimePay()))
            earnings.add(new String[]{"Overtime", money(p.getOvertimePay(), rs)});
        earnings.add(new String[]{"Performance Pay", money(p.getPerformancePay(), rs)});

        List<String[]> deductions = new ArrayList<>();
        deductions.add(new String[]{"PF", moneyDed(p.getPfDeduction(), rs)});
        deductions.add(new String[]{"ESI", moneyDed(p.getEsiDeduction(), rs)});
        deductions.add(new String[]{"Professional Tax", moneyDed(p.getPtDeduction(), rs)});
        deductions.add(new String[]{"Health Insurance", moneyDed(p.getHealthInsurance(), rs)});
        if (isPositive(p.getTdsDeduction()))
            deductions.add(new String[]{"TDS", moneyDed(p.getTdsDeduction(), rs)});
        deductions.add(new String[]{"Salary Advance", moneyDed(p.getSalaryAdvance(), rs)});
        if (isPositive(p.getOtherDeductions()))
            deductions.add(new String[]{"Other Deductions", moneyDed(p.getOtherDeductions(), rs)});

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{2.3f, 1.4f, 2.3f, 1.4f});

        // Header row
        t.addCell(headCell("Earnings", f, Element.ALIGN_LEFT));
        t.addCell(headCell("Amount", f, Element.ALIGN_RIGHT));
        t.addCell(headCell("Deductions", f, Element.ALIGN_LEFT));
        t.addCell(headCell("Amount", f, Element.ALIGN_RIGHT));

        // Body rows (align the two lists)
        int rows = Math.max(earnings.size(), deductions.size());
        for (int i = 0; i < rows; i++) {
            String[] e = i < earnings.size() ? earnings.get(i) : new String[]{"", ""};
            String[] d = i < deductions.size() ? deductions.get(i) : new String[]{"", ""};
            t.addCell(lineCell(e[0], f.body, Element.ALIGN_LEFT));
            t.addCell(lineCell(e[1], f.body, Element.ALIGN_RIGHT));
            t.addCell(lineCell(d[0], f.body, Element.ALIGN_LEFT));
            t.addCell(lineCell(d[1], f.body, Element.ALIGN_RIGHT));
        }

        // Totals band
        t.addCell(totalCell("Total Earnings", f.bodyBold, Element.ALIGN_LEFT));
        t.addCell(totalCell(money(p.getGrossSalary(), rs), f.bodyBold, Element.ALIGN_RIGHT));
        t.addCell(totalCell("Total Deductions", f.bodyBold, Element.ALIGN_LEFT));
        t.addCell(totalCell(moneyDed(p.getTotalDeductions(), rs), f.bodyBold, Element.ALIGN_RIGHT));

        // Net pay cell (right side, spanning the two deduction columns)
        PdfPCell netLabelSpacer = new PdfPCell(new Phrase(""));
        netLabelSpacer.setColspan(2);
        netLabelSpacer.setBorder(Rectangle.NO_BORDER);
        t.addCell(netLabelSpacer);
        t.addCell(netCell("Net Pay", f.bodyBold, Element.ALIGN_LEFT));
        t.addCell(netCell(money(p.getNetPay(), rs), f.bodyBold, Element.ALIGN_RIGHT));

        t.setSpacingAfter(18);
        return t;
    }

    /** Net pay in figures (large) and Indian words, centered. */
    private PdfPTable buildNetInWords(Payslip p, String rs, Fonts f) throws DocumentException {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);

        PdfPCell figure = new PdfPCell(new Phrase(money(p.getNetPay(), rs), f.h2));
        figure.setBorder(Rectangle.NO_BORDER);
        figure.setHorizontalAlignment(Element.ALIGN_CENTER);
        figure.setPaddingBottom(2);
        box.addCell(figure);

        PdfPCell words = new PdfPCell(new Phrase(rupeesInWords(p.getNetPay()), f.bodyBold));
        words.setBorder(Rectangle.NO_BORDER);
        words.setHorizontalAlignment(Element.ALIGN_CENTER);
        words.setPaddingBottom(6);
        box.addCell(words);

        box.setSpacingAfter(24);
        return box;
    }

    /** Employer + employee signature columns. */
    private PdfPTable buildSignatures(Payslip p, User user, String displayName, Fonts f)
            throws DocumentException {
        String designation = resolveDesignation(p, user);
        String company = firstNonBlank(p.getCompanyName(), COMPANY_NAME);
        String employeePhone = firstNonBlank(user.getPhone(), "-");

        PdfPTable sign = new PdfPTable(2);
        sign.setWidthPercentage(100);
        sign.setWidths(new float[]{1f, 1f});

        sign.addCell(signHeader("Employer Signature", f));
        sign.addCell(signHeader("Employee Signature", f));

        sign.addCell(signBlock(f, EMPLOYER_SIGN_NAME, EMPLOYER_SIGN_TITLE, company, EMPLOYER_SIGN_PHONE));
        sign.addCell(signBlock(f, nz(displayName), designation, company, employeePhone));

        return sign;
    }

    // ================================================================
    // Cell helpers
    // ================================================================

    private void metaRow(PdfPTable t, Fonts f, String l1, String v1, String l2, String v2,
                         boolean v2Right) {
        t.addCell(metaCell(l1, f.label, Element.ALIGN_LEFT));
        t.addCell(metaCell(v1, f.value, Element.ALIGN_LEFT));
        t.addCell(metaCell(l2, f.label, Element.ALIGN_LEFT));
        t.addCell(metaCell(v2, f.value, v2Right ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));
    }

    private PdfPCell metaCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPaddingTop(3);
        c.setPaddingBottom(3);
        return c;
    }

    private PdfPCell headCell(String text, Fonts f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f.bodyBold));
        c.setHorizontalAlignment(align);
        c.setPadding(6);
        c.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        c.setBorderColor(INK);
        c.setBorderWidthTop(1f);
        c.setBorderWidthBottom(1f);
        return c;
    }

    private PdfPCell lineCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPaddingLeft(6);
        c.setPaddingRight(6);
        c.setPaddingTop(4);
        c.setPaddingBottom(4);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        return c;
    }

    private PdfPCell totalCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(6);
        c.setBackgroundColor(BAND);
        c.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        c.setBorderColor(INK);
        return c;
    }

    private PdfPCell netCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(6);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(INK);
        return c;
    }

    private PdfPCell signHeader(String text, Fonts f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f.bodyBold));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPaddingBottom(42); // room for a physical signature
        return c;
    }

    private PdfPCell signBlock(Fonts f, String name, String title, String company, String phone) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.addElement(centered(name, f.bodyBold));
        c.addElement(centered(title, f.body));
        c.addElement(centered(company, f.body));
        c.addElement(centered(phone, f.body));
        return c;
    }

    private Paragraph centered(String text, Font font) {
        Paragraph para = new Paragraph(text, font);
        para.setAlignment(Element.ALIGN_CENTER);
        return para;
    }

    // ================================================================
    // Detail resolution (payslip override -> user field -> lookup -> "-")
    // ================================================================

    private String resolveDesignation(Payslip p, User user) {
        String d = firstNonBlank(p.getDesignation(), user.getDesignationTitle());
        if (isBlank(d) && user.getDesignationId() != null) {
            d = designationRepository.findById(user.getDesignationId())
                    .map(com.pixous.hrportal.modules.org.Designation::getName).orElse(null);
        }
        return isBlank(d) ? "-" : d;
    }

    private String resolveDepartment(Payslip p, User user) {
        String d = firstNonBlank(p.getDepartment(), user.getDepartmentTitle());
        if (isBlank(d) && user.getDepartmentId() != null) {
            d = departmentRepository.findById(user.getDepartmentId())
                    .map(com.pixous.hrportal.modules.org.Department::getName).orElse(null);
        }
        return isBlank(d) ? "-" : d;
    }

    private byte[] resolveLogo(Payslip p) {
        if (p.getCompanyLogo() != null && !p.getCompanyLogo().isBlank()) {
            try {
                return storageService.read(p.getCompanyLogo());
            } catch (Exception ignored) {
                // fall back to the bundled logo
            }
        }
        return LOGO_BYTES;
    }

    // ================================================================
    // Fonts (DejaVu for the rupee glyph; Helvetica fallback)
    // ================================================================

    /** Bundle of the fonts a payslip uses, built once per render. */
    private static final class Fonts {
        final boolean unicodeRupee;
        final Font h1;       // company name
        final Font h2;       // "Payslip" / net figure
        final Font tiny;     // address / gstin
        final Font label;    // meta labels
        final Font value;    // meta values
        final Font body;     // table body
        final Font bodyBold; // headers / totals

        Fonts() {
            BaseFont reg = regular();
            BaseFont bold = bold();
            this.unicodeRupee = reg != null && bold != null;
            if (unicodeRupee) {
                h1 = new Font(bold, 14, Font.NORMAL, INK);
                h2 = new Font(bold, 12, Font.NORMAL, INK);
                tiny = new Font(reg, 8, Font.NORMAL, MUTED);
                label = new Font(reg, 9, Font.NORMAL, MUTED);
                value = new Font(reg, 9.5f, Font.NORMAL, INK);
                body = new Font(reg, 9, Font.NORMAL, INK);
                bodyBold = new Font(bold, 9.5f, Font.NORMAL, INK);
            } else {
                h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, INK);
                h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, INK);
                tiny = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
                label = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
                value = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, INK);
                body = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
                bodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, INK);
            }
        }
    }

    private static synchronized BaseFont regular() {
        if (baseRegular == null && FONT_REGULAR != null) {
            try {
                baseRegular = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED, BaseFont.CACHED, FONT_REGULAR, null);
            } catch (Exception ignored) {
                return null;
            }
        }
        return baseRegular;
    }

    private static synchronized BaseFont bold() {
        if (baseBold == null && FONT_BOLD != null) {
            try {
                baseBold = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED, BaseFont.CACHED, FONT_BOLD, null);
            } catch (Exception ignored) {
                return null;
            }
        }
        return baseBold;
    }

    private static byte[] loadResource(String path) {
        try (InputStream in = ReportService.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    // ================================================================
    // Money / words / string helpers
    // ================================================================

    /** "₹ 27,500.00" (Indian digit grouping). */
    private static String money(BigDecimal v, String rs) {
        return rs + " " + groupIndian(v == null ? BigDecimal.ZERO : v);
    }

    /** Deduction display: "-₹ 5,000.00" when positive, "₹ 0.00" when zero. */
    private static String moneyDed(BigDecimal v, String rs) {
        if (v == null || v.signum() == 0) return rs + " 0.00";
        return "-" + rs + " " + groupIndian(v.abs());
    }

    /** Indian grouping (last 3 digits, then pairs): 1234567.89 -> 12,34,567.89. */
    private static String groupIndian(BigDecimal v) {
        v = v.abs().setScale(2, RoundingMode.HALF_UP);
        String plain = v.toPlainString();
        int dot = plain.indexOf('.');
        String intPart = plain.substring(0, dot);
        String dec = plain.substring(dot + 1);

        int len = intPart.length();
        String grouped;
        if (len <= 3) {
            grouped = intPart;
        } else {
            String last3 = intPart.substring(len - 3);
            String rest = intPart.substring(0, len - 3);
            StringBuilder r = new StringBuilder();
            int c = 0;
            for (int i = rest.length() - 1; i >= 0; i--) {
                r.insert(0, rest.charAt(i));
                if (++c % 2 == 0 && i != 0) r.insert(0, ',');
            }
            grouped = r + "," + last3;
        }
        return grouped + "." + dec;
    }

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
            "Eighteen", "Nineteen"};
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    /** "Twenty Thousand Seven Hundred Rupees" (+ " and NN Paise" when applicable). */
    private static String rupeesInWords(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = amount.longValue();
        int paise = amount.subtract(BigDecimal.valueOf(rupees)).movePointRight(2).abs().intValue();

        StringBuilder sb = new StringBuilder(indianWords(rupees)).append(" Rupees");
        if (paise > 0) sb.append(" and ").append(words99(paise)).append(" Paise");
        return sb.toString();
    }

    private static String indianWords(long n) {
        if (n == 0) return "Zero";
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000; n %= 10_000_000;
        long lakh = n / 100_000; n %= 100_000;
        long thousand = n / 1_000; n %= 1_000;
        long hundred = n / 100; n %= 100;
        if (crore > 0) sb.append(indianWords(crore)).append(" Crore ");
        if (lakh > 0) sb.append(words99((int) lakh)).append(" Lakh ");
        if (thousand > 0) sb.append(words99((int) thousand)).append(" Thousand ");
        if (hundred > 0) sb.append(ONES[(int) hundred]).append(" Hundred ");
        if (n > 0) sb.append(words99((int) n));
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String words99(int n) {
        if (n < 20) return ONES[n];
        return (TENS[n / 10] + (n % 10 > 0 ? " " + ONES[n % 10] : "")).trim();
    }

    private static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : b;
    }

    private static String nz(String s) {
        return isBlank(s) ? "-" : s;
    }

    // ================================================================
    // Excel exports (unchanged)
    // ================================================================

    public byte[] generateAttendanceReport(java.time.LocalDate from, java.time.LocalDate to, Long deptId) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Attendance Report");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Employee Code");
            header.createCell(1).setCellValue("Name");
            header.createCell(2).setCellValue("Date");
            header.createCell(3).setCellValue("Status");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL, "Failed to generate report");
        }
    }

    public byte[] generateLeaveReport(java.time.LocalDate from, java.time.LocalDate to, Long deptId) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Leave Report");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Employee Code");
            header.createCell(1).setCellValue("Name");
            header.createCell(2).setCellValue("Leave Type");
            header.createCell(3).setCellValue("From");
            header.createCell(4).setCellValue("To");
            header.createCell(5).setCellValue("Days");
            header.createCell(6).setCellValue("Status");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL, "Failed to generate report");
        }
    }

    public byte[] generatePayrollReport(int month, int year) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Payroll Report");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Employee Code");
            header.createCell(1).setCellValue("Name");
            header.createCell(2).setCellValue("Gross Salary");
            header.createCell(3).setCellValue("Total Deductions");
            header.createCell(4).setCellValue("Net Pay");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL, "Failed to generate report");
        }
    }
}
