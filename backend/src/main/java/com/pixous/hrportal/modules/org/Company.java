package com.pixous.hrportal.modules.org;

import com.pixous.hrportal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(name = "company_id", nullable = false, unique = true, length = 20)
    private String companyId; // Example: SETHU-8F42K7

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "legal_name", length = 150)
    private String legalName;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String website;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String timezone;

    @Column(length = 10)
    private String currency;

    @Column(name = "date_format", length = 20)
    private String dateFormat;

    @Column(length = 20)
    private String language;

    @Column(length = 50)
    private String industry;

    @Column(name = "organization_type", length = 50)
    private String organizationType;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE"; // ACTIVE, SUSPENDED, ARCHIVED

    // Branding
    @Column(name = "logo_path", length = 255)
    private String logoPath;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    @Column(name = "secondary_color", length = 20)
    private String secondaryColor;
}
