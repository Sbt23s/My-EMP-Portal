package com.pixous.hrportal.modules.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "holidays")
// Holidays belong to the company that declared them. Without this a second
// tenant would see the first tenant's calendar -- confirmed by test before the
// filter was added. The filter definition itself lives on User; this reuses it.
//
// "OR company_id IS NULL" is what makes switching it on safe: a row that has not
// been stamped stays visible to everyone rather than vanishing. V94 stamped every
// existing row, so in practice each company now sees only its own.
@org.hibernate.annotations.Filter(name = "tenantFilter",
        condition = "company_id = :companyId OR company_id IS NULL")
public class Holiday {

    /** Set on insert from the signed-in user's company. */
    @jakarta.persistence.Column(name = "company_id")
    private Long companyId;

    @jakarta.persistence.PrePersist
    void stampCompany() {
        if (companyId == null) companyId = com.pixous.hrportal.security.SecurityUtils.currentCompanyId();
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(length = 80)
    private String state;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
