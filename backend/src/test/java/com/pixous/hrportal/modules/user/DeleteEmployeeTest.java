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
        @DisplayName("A confirmation is still honoured when one is sent")
        void confirmationStillSupported() throws IOException {
            String service = read(SERVICE);

            // The screen no longer asks for a name, but the check remains for
            // any caller that does send one, and it is skipped when absent.
            assertThat(service).contains("if (confirmation != null)");
            assertThat(service).contains("to confirm");
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
            // TECHNICAL_ADMIN was already admitted here before this change, and
            // narrowing who may delete an employee is a separate decision from
            // adding the guards. Asserted so a later edit is deliberate rather
            // than accidental.
            assertThat(nearby)
                    .as("the existing platform-administrator access is unchanged")
                    .contains("TECHNICAL_ADMIN");
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
        @DisplayName("Deleting takes one confirm, with no name to type")
        void deleteTakesOneConfirm() throws IOException {
            String screen = read(SCREEN);

            int dialog = screen.indexOf("Delete {deleteTarget.name}?");
            assertThat(dialog).as("the list's delete dialog must exist").isGreaterThan(-1);
            String block = screen.substring(dialog, Math.min(dialog + 2000, screen.length()));

            // The typed name was removed deliberately. What must remain is a
            // dialog that states the consequence before it asks, so the single
            // click is still an informed one.
            //
            // Scoped to this dialog: the employee detail view has an older
            // delete of its own that asks for the employee code, and it is
            // untouched.
            assertThat(block)
                    .as("this confirm must not be gated on a typed name")
                    .doesNotContain("deleteConfirm");
            assertThat(block)
                    .as("the consequence is still stated before it asks")
                    .contains("There is no undo");
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
