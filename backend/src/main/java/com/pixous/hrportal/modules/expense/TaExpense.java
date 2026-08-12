package com.pixous.hrportal.modules.expense;

import com.pixous.hrportal.common.BaseEntity;
import com.pixous.hrportal.modules.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "ta_expenses")
public class TaExpense extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 150)
    private String location;

    /** Expense category: Petrol / House Rent / Snacks / Room / Construction Things / or free-text (Others). */
    @Column(length = 80)
    private String category;

    @Column(name = "starting_km")
    private Integer startingKm;

    @Column(name = "ending_km")
    private Integer endingKm;

    @Column(name = "total_km")
    private Integer totalKm;

    @Column(name = "hills_km")
    private Integer hillsKm;

    @Column(name = "plains_km")
    private Integer plainsKm;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "bus_fare")
    private BigDecimal busFare;

    @Column
    private BigDecimal others;

    @Column(name = "gross_total")
    private BigDecimal grossTotal;

    @Column(length = 255)
    private String remarks;

    @Column(name = "petrol_slip_path", length = 255)
    private String petrolSlipPath;

    @Column(columnDefinition = "TEXT")
    private String photos;

    @Column(length = 20, nullable = false)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    /** Why the claim was approved or rejected — required on rejection. */
    @Column(name = "decision_comment", length = 500)
    private String decisionComment;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private java.time.LocalDateTime decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
}
