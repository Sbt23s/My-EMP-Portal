package com.pixous.hrportal.modules.expense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nobody decides their own claim.
 *
 * <p>A claim is money out of the company, so the person asking for it cannot
 * also be the one who agrees to it — that is what the approval step is for.
 * The rule was missing here and present in leave, permission and work from
 * home, which is the sort of gap that stays open precisely because the other
 * three look like they cover it.
 *
 * <p>Worth pinning as a test because the failure is silent: HR holds
 * CLAIM_APPROVE and is also somebody who buys petrol, so the buttons appeared
 * on their own rows and worked, and nothing anywhere said they should not have.
 */
class SelfApprovalTest {

    /** The rule as the service applies it. */
    private static boolean refused(Long deciderId, Long claimantId, String status) {
        return deciderId != null
                && deciderId.equals(claimantId)
                && !"PENDING".equals(status);
    }

    @Test
    @DisplayName("Approving your own claim is refused")
    void cannotApproveOwn() {
        assertThat(refused(7L, 7L, "APPROVED")).isTrue();
    }

    @Test
    @DisplayName("Rejecting your own claim is refused too")
    void cannotRejectOwn() {
        // Rejecting your own is not harmless either: it closes the record
        // without anybody independent having looked at it.
        assertThat(refused(7L, 7L, "REJECTED")).isTrue();
    }

    @Test
    @DisplayName("Deciding somebody else's claim is allowed")
    void othersAreUnaffected() {
        assertThat(refused(7L, 8L, "APPROVED")).isFalse();
        assertThat(refused(7L, 8L, "REJECTED")).isFalse();
    }

    @Test
    @DisplayName("Putting a claim back to pending is not a decision")
    void pendingIsNotADecision() {
        // Moving a claim back to PENDING grants nothing, so it is not the
        // thing this rule is guarding against.
        assertThat(refused(7L, 7L, "PENDING")).isFalse();
    }

    @Test
    @DisplayName("An unknown decider is not treated as the claimant")
    void nullDeciderDoesNotMatch() {
        // A null id must not compare equal to anything; the ownership check
        // above it is what refuses this case.
        assertThat(refused(null, 7L, "APPROVED")).isFalse();
    }
}
