package com.pixous.hrportal.modules.leave;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "leave_types")
// Each company sets its own leave types and entitlements; one tenant's list was
// visible to another before this. Same pattern as Holiday -- see the note there
// for why the IS NULL clause matters when switching a filter on.
@org.hibernate.annotations.Filter(name = "tenantFilter",
        condition = "company_id = :companyId OR company_id IS NULL")
public class LeaveType {

    /** Set on insert from the signed-in user's company. */
    @Column(name = "company_id")
    private Long companyId;

    @PrePersist
    void stampCompany() {
        if (companyId == null) companyId = com.pixous.hrportal.security.SecurityUtils.currentCompanyId();
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "max_days_per_year")
    private Integer maxDaysPerYear;

    @Column(name = "carry_forward", nullable = false)
    private boolean carryForward = false;

    @Column(nullable = false)
    private boolean encashable = false;

    @Column(name = "gender_restriction")
    private Character genderRestriction;
    
    @Column(name = "allow_past_dates", nullable = false)
    private boolean allowPastDates = false;

    @Column(name = "accrual_type", length = 20)
    private String accrualType = "ANNUAL";

    @Column(name = "min_notice_days")
    private Integer minNoticeDays = 0;

    /** Max approvable days of this type per calendar month (null = no monthly cap). */
    @Column(name = "monthly_limit")
    private Integer monthlyLimit;

    @Column(nullable = false)
    private boolean active = true;

    /** Paid leave (no salary deduction). Unpaid types drive payslip Loss of Pay. */
    @Column(nullable = false)
    private boolean paid = false;
}