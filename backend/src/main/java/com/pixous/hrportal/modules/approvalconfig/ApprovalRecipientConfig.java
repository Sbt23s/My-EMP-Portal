package com.pixous.hrportal.modules.approvalconfig;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One tick in the administrator's approval configuration: for this module,
 * this role may be addressed.
 *
 * <p>See V151 for why this narrows the dropdowns rather than producing them,
 * and why a module with no rows is unrestricted.
 */
@Entity
@Table(name = "approval_recipient_config")
@Getter
@Setter
public class ApprovalRecipientConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    /** HELPDESK | COMPLAINT | PERMISSION | LEAVE | WFH */
    @Column(name = "module_code", nullable = false, length = 40)
    private String moduleCode;

    /**
     * A role code, or the pseudo-role CTO.
     *
     * <p>CTO is an employee code in this schema and not a role, so it has no
     * row in {@code roles}. It is spelled here as though it were one, because
     * from the configuring administrator's point of view it is the same kind
     * of choice — a recipient to tick or untick — and splitting the table in
     * two to honour a schema detail would push that detail into the screen.
     */
    @Column(name = "role_code", length = 40)
    private String roleCode;

    /**
     * One named person, when the rule is about them rather than a role.
     *
     * <p>A row is either a role rule or a person rule: exactly one of
     * {@code roleCode} and this is set. Both kinds may sit against one module
     * and the reader treats them as alternatives -- offered if a role matches
     * OR a person is named.
     *
     * <p>Both exist because they answer differently over time. "Whoever is on
     * the HR desk" survives somebody joining or leaving; a list of three names
     * does not, and would quietly stop offering the new joiner. Which one fits
     * is the administrator's call, per module.
     */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
