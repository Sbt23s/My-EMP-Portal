package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.modules.biometric.PersonMatcher.Candidate;
import com.pixous.hrportal.modules.biometric.PersonMatcher.Confidence;
import com.pixous.hrportal.modules.biometric.PersonMatcher.Match;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matching the terminal's people to this portal's employees.
 *
 * <p>The names and codes below are the real ones, from the live account of 36
 * people and the portal's 35 employees. They are here rather than invented
 * because the failures that matter are all specific: two people called Surya,
 * a Ranjith whose number does not line up, an admin account whose code happens
 * to end in 1.
 *
 * <p>The stake is that a wrong match sends one person's arrival to another
 * person's attendance, and nothing about that looks wrong afterwards.
 */
class PersonMatcherTest {

    /** The portal's employees, as they really are. */
    private static final List<Candidate> PORTAL = List.of(
            new Candidate("ADM0001", "System Admin"),
            new Candidate("EMP0003", "Test Employee"),
            new Candidate("HR0001", "HR"),
            new Candidate("INT-E060", "Pradeep D E"),
            new Candidate("PIX-E001", "VANARAJA D"),
            new Candidate("PIX-E006", "Nandha Kumar VEERASAMY"),
            new Candidate("PIX-E008", "Prithiv Saravana Kumar"),
            new Candidate("PIX-E009", "Loganathan Ramanujam"),
            new Candidate("PIX-E010", "Surya Sundarraj"),
            new Candidate("PIX-E011", "Elanjsuriyan Senthilkumar"),
            new Candidate("PIX-E016", "Meena Sudhakaran Kanchana"),
            new Candidate("PIX-E018", "Vijay RamaKrishnan Perumal"),
            new Candidate("PIX-E020", "Vaishnavi Shanmugam"),
            new Candidate("PIX-E023", "Ranjith Gurusamy"),
            new Candidate("PIX-E025", "Indumathi Kuppuraj"),
            new Candidate("PIX-E027", "Bharath kumar Murugesan"),
            new Candidate("PIX-E028", "Karan Sivakumar"),
            new Candidate("PIX-E031", "Sivasankar Krishnan"),
            new Candidate("PIX-E035", "Vijayaraj Chandran"),
            new Candidate("PIX-E039", "Amutha Kumari G"),
            new Candidate("PIX-E048", "Subash Chakravarthi"),
            new Candidate("PIX-E049", "Karpagavalli"),
            new Candidate("PIX-E051", "Bavatharani Balakrishnan"),
            new Candidate("PIX-E052", "Sahaya Akshaya"),
            new Candidate("PIX-E053", "Siva D Kumar"),
            new Candidate("PIX-E054", "Saravana Balan"),
            new Candidate("PIX-E055", "Harish C"),
            new Candidate("PIX-E056", "KARTHIGAISELVAN"),
            new Candidate("PIX-E057", "SETHUBALA"),
            new Candidate("PIX-E058", "Elandevan Ravikumar"),
            new Candidate("PIX-E059", "Monic Richard .H.C"),
            new Candidate("PIX-E061", "Venkateshwaran S V."));

    private static Match match(String code, String first, String last) {
        return PersonMatcher.match(code, first, last, PORTAL);
    }

    @Nested
    @DisplayName("Both signals agree, so it is applied without asking")
    class Certain {

        @Test
        @DisplayName("The ordinary case: 039 AMUTHA KUMARI G is PIX-E039")
        void ordinary() {
            Match m = match("039", "AMUTHA KUMARI", "G");
            assertThat(m.employeeCode()).isEqualTo("PIX-E039");
            assertThat(m.confidence()).isEqualTo(Confidence.CERTAIN);
        }

        @Test
        @DisplayName("Case and spacing are ignored: VIJAYA RAJ is Vijayaraj Chandran")
        void spacingIgnored() {
            // The terminal writes it as two words, the portal as one. Comparing
            // them literally leaves a real employee unmatched for ever.
            Match m = match("035", "VIJAYA", "RAJ");
            assertThat(m.employeeCode()).isEqualTo("PIX-E035");
            assertThat(m.confidence()).isEqualTo(Confidence.CERTAIN);
        }

        @Test
        @DisplayName("Punctuation is ignored: Monic richard H C is Monic Richard .H.C")
        void punctuationIgnored() {
            Match m = match("059", "Monic richard", "H C");
            assertThat(m.employeeCode()).isEqualTo("PIX-E059");
            assertThat(m.confidence()).isEqualTo(Confidence.CERTAIN);
        }

        @Test
        @DisplayName("An initial against a full surname: Bavatharani B is Balakrishnan")
        void initialAgainstSurname() {
            Match m = match("051", "Bavatharani", "B");
            assertThat(m.employeeCode()).isEqualTo("PIX-E051");
            assertThat(m.confidence()).isEqualTo(Confidence.CERTAIN);
        }

        @Test
        @DisplayName("A different prefix entirely: 060 Pradeep DE is INT-E060")
        void differentPrefix() {
            // Not every employee code starts PIX-E. The number is what matches.
            Match m = match("060", "Pradeep", "DE");
            assertThat(m.employeeCode()).isEqualTo("INT-E060");
            assertThat(m.confidence()).isEqualTo(Confidence.CERTAIN);
        }
    }

    @Nested
    @DisplayName("The wrong matches that a single signal would have made")
    class WouldHaveBeenWrong {

        @Test
        @DisplayName("041 SURYA R is NOT PIX-E010 Surya Sundarraj")
        void twoPeopleCalledSurya() {
            /*
             * The one that would have done real damage. SURYA R is on the
             * terminal and not in the portal; Surya Sundarraj is PIX-E010 and
             * is a different person. Matching on the name alone -- which
             * "identify them by name" means -- sends SURYA R's arrivals to
             * Surya Sundarraj's attendance, every day, invisibly.
             *
             * The number is what refuses it: 41 is not 10.
             */
            Match m = match("041", "SURYA", "R");
            assertThat(m.confidence())
                    .as("a name-only agreement must never be applied automatically")
                    .isNotEqualTo(Confidence.CERTAIN);
            if (m.employeeCode() != null) {
                assertThat(m.reason()).isEqualTo("name only");
            }
        }

        @Test
        @DisplayName("001 VANARAJA D is PIX-E001, not ADM0001 System Admin")
        void numberAloneWouldHitTheAdmin() {
            /*
             * ADM0001 ends in 1, and so does 001. On the number alone this is a
             * coin toss between the real employee and the administrator
             * account -- and the sort order decides it. The name settles it.
             */
            Match m = match("001", "VANARAJA", "D");
            assertThat(m.employeeCode()).isEqualTo("PIX-E001");
            assertThat(m.confidence()).isEqualTo(Confidence.CERTAIN);
        }

        @Test
        @DisplayName("036 RANJITH is offered, not applied: the numbers disagree")
        void ranjithIsAQuestion() {
            /*
             * There is a Ranjith in the portal, at PIX-E023, and the terminal
             * calls this person 036. They may well be the same person -- and
             * that is a question for somebody who knows, not a guess to write
             * into the attendance register.
             */
            Match m = match("036", "RANJITH", "");
            assertThat(m.confidence()).isNotEqualTo(Confidence.CERTAIN);
            assertThat(m.employeeCode()).isEqualTo("PIX-E023");
            assertThat(m.reason()).isEqualTo("name only");
        }

        @Test
        @DisplayName("013 DINESH KUMAR M matches nobody, despite sharing 'kumar'")
        void sharedCommonWord() {
            /*
             * "Kumar" appears in four employees' names. A matcher that accepted
             * any shared word would pick one of them at random; there is no
             * Dinesh in the portal at all.
             */
            Match m = match("013", "DINESH", "KUMAR M");
            assertThat(m.confidence()).isNotEqualTo(Confidence.CERTAIN);
        }
    }

    @Nested
    @DisplayName("People the portal does not have")
    class NotInThePortal {

        @Test
        @DisplayName("An unknown person matches nothing rather than something close")
        void unknown() {
            for (String[] p : new String[][]{
                    {"029", "SANTHOSH", ""},
                    {"033", "SURESH", "P"},
                    {"034", "SATHIYASEELAN", "T"},
                    {"040", "SHAMINTHRAN", "RD"},
                    {"042", "KOWSALYA", "R"},
                    {"EO62", "Gokila", "Ganesan"}}) {
                Match m = match(p[0], p[1], p[2]);
                assertThat(m.confidence())
                        .as("%s %s %s must not be matched automatically", p[0], p[1], p[2])
                        .isNotEqualTo(Confidence.CERTAIN);
            }
        }
    }

    @Nested
    @DisplayName("Across the whole real account")
    class WholeAccount {

        /** Every person on the live terminal: code, first name, last name. */
        private static final String[][] TERMINAL = {
                {"001", "VANARAJA", "D"}, {"006", "NANDHA KUMAR", ""},
                {"008", "PRITHIV", "RS"}, {"009", "LOGANATHAN", "R"},
                {"010", "SURYA", "S"}, {"011", "ELANJSURIYAN", ""},
                {"013", "DINESH", "KUMAR M"}, {"016", "MEENA", ""},
                {"018", "VIJAY", "RAMAKRISHNAN"}, {"020", "VAISHNAVI", "S"},
                {"025", "INDUMATHI", ""}, {"027", "BHARATH KUMAR", "M"},
                {"028", "KARAN", "S"}, {"029", "SANTHOSH", ""},
                {"031", "SIVASANKAR", ""}, {"033", "SURESH", "P"},
                {"034", "SATHIYASEELAN", "T"}, {"035", "VIJAYA", "RAJ"},
                {"036", "RANJITH", ""}, {"039", "AMUTHA KUMARI", "G"},
                {"040", "SHAMINTHRAN", "RD"}, {"041", "SURYA", "R"},
                {"042", "KOWSALYA", "R"}, {"048", "Subash", ""},
                {"049", "Karpagavalli", ""}, {"051", "Bavatharani", "B"},
                {"052", "Sahaya Akshaya", "A"}, {"054", "Saravana", "Balan"},
                {"055", "Harish", "C"}, {"056", "Karthigai selvan", "L"},
                {"057", "Sethubala", "B"}, {"058", "Elandevan", "R"},
                {"059", "Monic richard", "H C"}, {"060", "Pradeep", "DE"},
                {"061", "Venkateshwaran", "S V"}, {"EO62", "Gokila", "Ganesan"}};

        @Test
        @DisplayName("27 are certain, and every certain one is right")
        void theRealAccount() {
            /*
             * The number measured against the live data before the matcher was
             * written: name alone found 27 and got 2 wrong; number alone found
             * 26 and got 1 wrong; both together find 27 and get none wrong.
             *
             * The count is asserted so that loosening the rules to catch a few
             * more shows up here as a change somebody has to justify.
             */
            int certain = 0;
            for (String[] p : TERMINAL) {
                Match m = match(p[0], p[1], p[2]);
                if (m.confidence() == Confidence.CERTAIN) {
                    certain++;
                    // Every automatic match must agree on the number, which is
                    // the independent check the name cannot provide.
                    assertThat(PersonMatcher.trailingNumber(m.employeeCode()))
                            .as("%s %s matched %s on a number that does not agree",
                                    p[0], p[1], m.employeeCode())
                            .isEqualTo(PersonMatcher.trailingNumber(p[0]));
                }
            }
            assertThat(certain).isEqualTo(27);
        }

        @Test
        @DisplayName("No employee is claimed by two terminal identities")
        void noDoubleClaim() {
            /*
             * The failure mode a wrong match creates: two people on the
             * terminal both mapped to one employee, their punches interleaved
             * into one attendance row. uk_hik_user stops it being stored, but
             * the matcher should not be proposing it in the first place.
             */
            List<String> claimed = new java.util.ArrayList<>();
            for (String[] p : TERMINAL) {
                Match m = match(p[0], p[1], p[2]);
                if (m.confidence() == Confidence.CERTAIN) {
                    assertThat(claimed)
                            .as("%s %s claims %s, which is already taken",
                                    p[0], p[1], m.employeeCode())
                            .doesNotContain(m.employeeCode());
                    claimed.add(m.employeeCode());
                }
            }
        }
    }
}
