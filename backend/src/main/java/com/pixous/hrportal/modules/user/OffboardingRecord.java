package com.pixous.hrportal.modules.user;

import java.time.LocalDate;

import com.pixous.hrportal.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "offboarding_records")
public class OffboardingRecord extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "relieving_date", nullable = false)
    private LocalDate relievingDate;

    @Column(length = 150)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "fnf_status", nullable = false, length = 20)
    private String fnfStatus = "PENDING";
}
