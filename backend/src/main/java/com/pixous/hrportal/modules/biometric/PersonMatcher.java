package com.pixous.hrportal.modules.biometric;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which employee a person on the terminal is.
 *
 * <p>The integration was designed around a shared employee number, and on a
 * terminal that predates the portal there is none: Hikvision refuses to change
 * an employee number once a person exists ({@code /persons/update} answers
 * OPEN000010; §5.8.6 says "The employee No. cannot be edited"). The live
 * account holds "001" and "039" while the portal holds "PIX-E001" and
 * "PIX-E039".
 *
 * <h2>Why two signals and not one</h2>
 *
 * <p>Both were measured against the real account of 36 people before this was
 * written, because both look sufficient and neither is.
 *
 * <p><b>The number alone</b> matched 26, and matched one of them wrongly:
 * terminal "001" (VANARAJA D) also matches "ADM0001" (System Admin), a
 * different person. A trailing number is a weak identifier when codes have
 * different prefixes.
 *
 * <p><b>The name alone</b> matched 27 and got two wrong. The worst was terminal
 * "041" (SURYA R), who does not exist in the portal, being matched to
 * "PIX-E010" (Surya Sundarraj) — a real employee whose attendance would then
 * have carried somebody else's punches.
 *
 * <p><b>Both together</b> matched 27 with none wrong, and the two failures of
 * the name pass were correctly withheld: SURYA R has no matching number, and
 * RANJITH's number does not match the Ranjith in the portal — which is a real
 * question for a person to answer, not one to guess at.
 *
 * <p>So a match is automatic only when the number and the name independently
 * point at the same employee. Everything else is offered as a suggestion for
 * somebody to confirm, and nothing is written on a suggestion alone.
 */
public final class PersonMatcher {

    /** How the match was reached, so a screen can say why it is suggesting one. */
    public enum Confidence {
        /** Number and name both point here. Safe to apply without asking. */
        CERTAIN,
        /** One of the two points here. A person should look. */
        SUGGESTED,
        /** Nothing points here. */
        NONE
    }

    /**
     * @param employeeCode the portal employee this points at, or null
     * @param confidence   how it was reached
     * @param reason       words for a screen: "number and name", "name only"
     */
    public record Match(String employeeCode, Confidence confidence, String reason) {
        static final Match NOTHING = new Match(null, Confidence.NONE, "no match");
    }

    /** One employee, as this matcher needs to see them. */
    public record Candidate(String employeeCode, String name) {}

    private static final Pattern TRAILING_DIGITS = Pattern.compile("(\\d+)\\s*$");
    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z]");
    private static final Pattern WORDS = Pattern.compile("[a-z]+");

    private PersonMatcher() {
    }

    /**
     * The best employee for one terminal person.
     *
     * @param personCode the terminal's employee number, e.g. "001"
     * @param firstName  as the terminal holds it
     * @param lastName   as the terminal holds it, often just initials
     * @param candidates every employee to consider
     */
    public static Match match(String personCode, String firstName, String lastName,
                              List<Candidate> candidates) {
        Integer number = trailingNumber(personCode);
        String full = letters(firstName) + letters(lastName);
        String first = letters(firstName);
        Set<String> nameWords = realWords(firstName + " " + lastName);

        List<Candidate> byNumber = new ArrayList<>();
        List<Candidate> byName = new ArrayList<>();

        for (Candidate c : candidates) {
            if (number != null && number.equals(trailingNumber(c.employeeCode()))) {
                byNumber.add(c);
            }
            if (namesAgree(full, first, nameWords, c.name())) {
                byName.add(c);
            }
        }

        /*
         * Both signals, one employee. The only case written without asking.
         *
         * Exactly one candidate must carry the number, and that same candidate
         * must be among the ones the name reaches. The name list is allowed to
         * be longer -- "Nandha Kumar" also brushes "Bharath kumar Murugesan" --
         * because the number is what makes the choice; the name only has to
         * agree with it.
         *
         * Requiring the name list to hold exactly one was the first attempt and
         * it was too strict: it refused 5 of the 27 people the live account
         * matches cleanly, including 001 VANARAJA D, purely because a common
         * surname appeared elsewhere.
         */
        /*
         * Several employees share the trailing number, which happens because
         * the codes have different prefixes: terminal "001" matches both
         * "PIX-E001" (VANARAJA D) and "ADM0001" (System Admin). The name is
         * what separates them, and it does so cleanly -- so narrow the
         * number list by the name before giving up on it.
         *
         * Without this, the one person whose number collides with the
         * administrator account is the one person left unmatched, which is
         * both wrong and the least obvious possible outcome.
         */
        if (byNumber.size() > 1) {
            List<Candidate> narrowed = new ArrayList<>();
            for (Candidate c : byNumber) {
                if (byName.stream().anyMatch(n -> n.employeeCode().equals(c.employeeCode()))) {
                    narrowed.add(c);
                }
            }
            if (narrowed.size() == 1) {
                return new Match(narrowed.get(0).employeeCode(),
                        Confidence.CERTAIN, "number and name");
            }
        }

        if (byNumber.size() == 1) {
            String code = byNumber.get(0).employeeCode();
            boolean nameAgrees = byName.stream()
                    .anyMatch(c -> c.employeeCode().equals(code));
            if (nameAgrees) {
                return new Match(code, Confidence.CERTAIN, "number and name");
            }
            if (byName.isEmpty()) {
                // The number alone. Offered, because a terminal person with no
                // name filled in is a real case and the number may well be right.
                return new Match(code, Confidence.SUGGESTED, "number only");
            }
            // The number says one person and the name says others. The most
            // suspicious shape there is -- neither is applied.
            return new Match(code, Confidence.SUGGESTED, "number only, name disagrees");
        }

        // No usable number. A single unambiguous name is a suggestion, never
        // more: two employees called Surya are how a name-only match sends one
        // person's arrivals to another person's attendance.
        if (byName.size() == 1) {
            return new Match(byName.get(0).employeeCode(), Confidence.SUGGESTED, "name only");
        }
        return Match.NOTHING;
    }

    /**
     * Whether two names describe the same person.
     *
     * <p>Case, spacing and punctuation are all ignored, because the two sides
     * write the same person three different ways: "VIJAYA RAJ" against
     * "Vijayaraj Chandran", "Monic richard H C" against "Monic Richard .H.C",
     * "Sethubala B" against "SETHUBALA".
     *
     * <p>Checked in the order a person would: the whole name, then one being
     * the beginning of the other, then the first name, then a shared full word.
     * A single initial is never enough on its own — "SURYA R" and "Surya S"
     * share "Surya" and are two people.
     */
    private static boolean namesAgree(String full, String first,
                                      Set<String> words, String candidateName) {
        String other = letters(candidateName);
        if (full.isEmpty() || other.isEmpty()) {
            return false;
        }
        if (full.equals(other)) {
            return true;
        }
        // One is the start of the other: the portal often carries a surname the
        // terminal abbreviates to an initial, and vice versa.
        if (full.length() >= 5 && (other.startsWith(full) || full.startsWith(other))) {
            return true;
        }
        if (first.length() >= 4 && other.startsWith(first)) {
            return true;
        }
        // A shared word of four letters or more. Deliberately not three: "raj",
        // "kumar" and "devi" are common enough that a short shared word says
        // very little.
        Set<String> otherWords = realWords(candidateName);
        for (String w : words) {
            if (w.length() >= 4 && otherWords.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** The number at the end of a code: "PIX-E039" and "039" are both 39. */
    static Integer trailingNumber(String code) {
        if (code == null) {
            return null;
        }
        Matcher m = TRAILING_DIGITS.matcher(code.trim());
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.valueOf(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Letters only, lower case: "Monic Richard .H.C" becomes "monicrichardhc". */
    static String letters(String s) {
        if (s == null) {
            return "";
        }
        return NON_LETTERS.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** Words of three letters or more; initials carry almost no evidence. */
    static Set<String> realWords(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) {
            return out;
        }
        Matcher m = WORDS.matcher(s.toLowerCase(Locale.ROOT));
        while (m.find()) {
            if (m.group().length() > 2) {
                out.add(m.group());
            }
        }
        return out;
    }
}
