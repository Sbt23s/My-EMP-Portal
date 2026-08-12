package com.pixous.hrportal.modules.leave;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "leave_balances")
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private BigDecimal allocated = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal used = BigDecimal.ZERO;

    @Transient
    public BigDecimal getAvailable() {
        return allocated.subtract(used);
    }
}
