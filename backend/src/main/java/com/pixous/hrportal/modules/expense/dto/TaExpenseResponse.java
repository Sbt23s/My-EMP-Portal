package com.pixous.hrportal.modules.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaExpenseResponse(
    Long id,
    Long userId,
    String userName,
    String employeeCode,
    String team,
    LocalDate date,
    String location,
    String category,
    Integer startingKm,
    Integer endingKm,
    Integer totalKm,
    Integer hillsKm,
    Integer plainsKm,
    BigDecimal totalAmount,
    BigDecimal busFare,
    BigDecimal others,
    BigDecimal grossTotal,
    String remarks,
    String status,
    String petrolSlipPath,
    String photos,
    String decisionComment,
    String decidedByName,
    LocalDateTime decidedAt
) {}
