package com.pixous.hrportal.modules.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting an employee permanently.
 *
 * <p>This is the most destructive operation in the portal. Forty-two tables
 * cascade from users -- attendance, leave requests and balances, payslips,
 * salary structures, expense claims, tickets, tasks, work reports, performance
 * reviews, documents, bank details -- and Flyway cannot roll any of it back.
 *
 * <p>So the guards are the feature. These assert the ones that stop a misclick
 * or a compliance problem, against the source, so they hold on any machine
 * without a database.
 */
class DeleteEmployeeTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/com/pixous/hrportal/modules/user/UserService.java");
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/pixous/hrportal/modules/user/UserController.java");
    private static final Path SCREEN = Path.of(
            "../web/src/pages/Employees.tsx");

    private String read(Path p) throws IOException {
        assertThat(p).as("%s must exist", p).exists();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("Guards on the server")
    class ServerGuards {

        @Test
        @DisplayName("An employee with payslips is refused")
        void payrollHistoryIsProtected() throws IOException {
            String service = read(SERVICE);

            /*
             * Payslips carry PF and ESI figures an employer must be able to
             * produce years later. Cascading them away to tidy a list is the
             * kind of deletion nobody notices until an audit asks for them.
             */
            assertThat(service)
                    .as("payslips must be counted before anything is deleted")
                    .contains("countPayslips");
            assertThat(service)
                    .as("the refusal must name PF and ESI so the reason is obvious")
                    .contains("PF and ESI");
            assertThat(service)
                    .as("the refusal must point at offboarding as the alternative")
                    .contains("Offboard them instead");
        }

        @Test
        @DisplayName("The employee's name must be typed to confirm")
        void nameConfirmationRequired() throws IOException {
            // A destructive button next to Edit on a crowded table is easy to
            // hit by accident, and there is no undo.
            assertThat(read(SERVICE))
                    .as("a mismatched confirmation must refuse")
                    .contains("to confirm");
        }

        @Test
        @DisplayName("Nobody can delete their own account")
        void cannotDeleteSelf() throws IOException {
            assertThat(read(SERVICE)).contains("You cannot delete your own account");
        }

        @Test
        @DisplayName("The deletion is audited before the row is removed")
        void auditedBeforeDeletion() throws IOException {
            String service = read(SERVICE);

            int audit = service.indexOf("EMPLOYEE_DELETED");
            int delete = service.indexOf("userRepository.delete(user)");

            assertThat(audit).as("the deletion must be audited").isGreaterThan(-1);
            assertThat(delete).as("the deletion must happen").isGreaterThan(-1);
            // Afterwards there is nothing left to describe: the name, the code
            // and who did it are the whole point of the entry.
            assertThat(audit).as("the audit entry must be written before the delete")
                    .isLessThan(delete);
        }

        @Test
        @DisplayName("Only HR permissions reach the endpoint")
        void endpointIsGated() throws IOException {
            String controller = read(CONTROLLER);
            int mapping = controller.indexOf("@DeleteMapping(\"/{id}\")");
            assertThat(mapping).as("the delete endpoint must exist").isGreaterThan(-1);

            String nearby = controller.substring(mapping, Math.min(mapping + 300, controller.length()));
            assertThat(nearby)
                    .as("guarded by the same pair that guards every other employee write")
                    .contains("USER_MANAGE")
                    .contains("EMPLOYEE_MANAGE");
            // This is an HR decision about one company's staff, not a platform
            // operation, so the technical administrator is deliberately absent.
            assertThat(nearby)
                    .as("a technical administrator is not an HR user")
                    .doesNotContain("TECHNICAL_ADMIN");
        }
    }

    @Nested
    @DisplayName("The screen")
    class Screen {

        @Test
        @DisplayName("The button is shown only to somebody who may manage employees")
        void buttonIsGated() throws IOException {
            String screen = read(SCREEN);
            int button = screen.indexOf("setDeleteTarget({");
            assertThat(button).as("the delete button must exist").isGreaterThan(-1);

            // The server decides, but a button everybody can see and nobody can
            // use is its own kind of wrong.
            String before = screen.substring(Math.max(0, button - 400), button);
            assertThat(before).contains("canManage");
        }

        @Test
        @DisplayName("The confirm button stays disabled until the name matches")
        void confirmRequiresTheName() throws IOException {
            assertThat(read(SCREEN))
                    .as("typing anything else must not enable the button")
                    .contains("deleteConfirm.trim().toLowerCase() !== deleteTarget.name.trim().toLowerCase()");
        }

        @Test
        @DisplayName("The dialog says there is no undo, and offers offboarding")
        void dialogExplainsTheConsequence() throws IOException {
            String screen = read(SCREEN);
            assertThat(screen).contains("There is no undo");
            assertThat(screen).contains("offboard");
        }
    }
}
