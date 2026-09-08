package com.pixous.hrportal.modules.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which words a changed path is recorded under.
 *
 * <p>{@code describe} returns the first rule whose prefix matches, walking the
 * list in order -- it does not sort by length. So the order of the list *is*
 * the behaviour, and a rule added in the wrong place silently steals the label
 * from a more specific one. That failure is invisible: the row is still
 * written, it just says the wrong thing, and nobody looks at the audit log
 * until they need it.
 *
 * <p>Configuration changes used to fall through to SYSTEM / "Change", which is
 * also what a task edit produced. These assertions pin the labels that make a
 * whole-company change findable.
 */
class AuditFilterRulesTest {

    /** {@code describe} is private and static; this is the behaviour under test. */
    private static String[] describe(String path) {
        try {
            Method m = AuditFilter.class.getDeclaredMethod("describe", String.class);
            m.setAccessible(true);
            return (String[]) m.invoke(null, path);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("describe(String) is gone or changed shape", e);
        }
    }

    private static String category(String path) {
        return describe(path)[0];
    }

    private static String label(String path) {
        return describe(path)[1];
    }

    @Nested
    @DisplayName("Configuration is named, not swept into SYSTEM")
    class ConfigIsNamed {

        @Test
        @DisplayName("Turning a module off for a company is a configuration change")
        void moduleConfig() {
            String path = "/api/technical-admin/companies/7/modules";
            assertThat(category(path)).isEqualTo(AuditService.CONFIG);
            assertThat(label(path)).isEqualTo("Changed a company's configuration");
        }

        @Test
        @DisplayName("Editing a role's permissions is a configuration change")
        void roleConfig() {
            assertThat(category("/api/technical-admin/roles/4")).isEqualTo(AuditService.CONFIG);
            assertThat(label("/api/technical-admin/roles/4"))
                    .isEqualTo("Changed a role's permissions");
        }

        @Test
        @DisplayName("Portal settings, module access and team leaders are all configuration")
        void otherConfig() {
            assertThat(category("/api/settings")).isEqualTo(AuditService.CONFIG);
            assertThat(category("/api/my-modules")).isEqualTo(AuditService.CONFIG);
            assertThat(category("/api/team-leaders/3")).isEqualTo(AuditService.CONFIG);
            assertThat(category("/api/chatbot/settings")).isEqualTo(AuditService.CONFIG);
        }

        @Test
        @DisplayName("None of them still reads as the generic fallback")
        void notTheFallback() {
            // The bug being fixed: every one of these produced SYSTEM/"Change",
            // the same row a task edit produces.
            for (String path : new String[]{
                    "/api/settings", "/api/my-modules", "/api/team-leaders/3",
                    "/api/technical-admin/roles/4", "/api/technical-admin/companies/7/modules",
                    "/api/global-announcements/2"}) {
                assertThat(label(path))
                        .as("label for " + path)
                        .isNotEqualTo("Change");
            }
        }
    }

    @Nested
    @DisplayName("Order decides the label, so the specific rule must win")
    class OrderMatters {

        @Test
        @DisplayName("A company's sub-resource is not merely 'the company list'")
        void subResourceBeatsCollection() {
            /*
             * The trailing-slash rule exists for this: without it, a module
             * change under /companies/7/modules matched the /companies rule and
             * was recorded as an edit to the company list.
             */
            assertThat(label("/api/technical-admin/companies/7/modules"))
                    .isEqualTo("Changed a company's configuration");
            assertThat(label("/api/technical-admin/companies"))
                    .isEqualTo("Changed the company list");
        }

        @Test
        @DisplayName("Payroll's specific rules still beat the general payroll rule")
        void payrollOrderIntact() {
            // Pre-existing behaviour the new rules must not have disturbed.
            assertThat(label("/api/payroll/payslip/generate")).isEqualTo("Generated a payslip");
            assertThat(label("/api/payroll/salary")).isEqualTo("Changed a salary structure");
            assertThat(label("/api/payroll/salary-months")).isEqualTo("Set month-wise basic pay");
            assertThat(label("/api/payroll/anything-else")).isEqualTo("Payroll change");
        }

        @Test
        @DisplayName("Employee and security rules still beat their general ones")
        void employeeOrderIntact() {
            assertThat(label("/api/users/password")).isEqualTo("Changed a password");
            assertThat(category("/api/users/password")).isEqualTo(AuditService.SECURITY);
            assertThat(label("/api/users/9")).isEqualTo("Changed an employee record");
            assertThat(label("/api/auth/login")).isEqualTo("Signed in");
        }

        @Test
        @DisplayName("The biometric mapping is an attendance action, and the specific rule wins")
        void biometricOrder() {
            /*
             * Remapping a terminal identity decides whose attendance a punch
             * becomes, so it belongs with attendance rather than with the
             * settings screens. Both specific rules sit above the general
             * /api/biometric one, which is the whole of the behaviour: put
             * either one below and it never matches.
             */
            assertThat(category("/api/biometric/admin/sync"))
                    .isEqualTo(AuditService.ATTENDANCE);
            assertThat(label("/api/biometric/admin/sync"))
                    .isEqualTo("Synced the biometric person mapping");
            assertThat(label("/api/biometric/admin/sync-enrolment"))
                    .isEqualTo("Refreshed biometric enrolment");
            assertThat(label("/api/biometric/admin/status"))
                    .isEqualTo("Biometric attendance change");

            // And it has not stolen the label from attendance's own rules.
            assertThat(label("/api/attendance/punch-in")).isEqualTo("Punched in");
        }

        @Test
        @DisplayName("The technical administrator's own sign-in stays under SECURITY")
        void techAdminAuthIsSecurity() {
            // Under CONFIG it would be filed with the settings screens, when it
            // is really an account action.
            assertThat(category("/api/technical-admin/auth/login"))
                    .isEqualTo(AuditService.SECURITY);
        }
    }

    @Nested
    @DisplayName("Anything unmatched still gets recorded")
    class Fallback {

        @Test
        @DisplayName("An unknown path is a SYSTEM change rather than nothing")
        void unknownPath() {
            /*
             * The floor the filter exists for. A new endpoint nobody has added
             * a rule for is still written to the log -- vaguely, but written --
             * because an audit trail with silent holes is worse than one with
             * imprecise rows.
             */
            assertThat(category("/api/something-invented-tomorrow"))
                    .isEqualTo(AuditService.SYSTEM);
            assertThat(label("/api/something-invented-tomorrow")).isEqualTo("Change");
        }
    }
}
