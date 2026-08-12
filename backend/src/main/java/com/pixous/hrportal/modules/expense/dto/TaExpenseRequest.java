package com.pixous.hrportal.modules.expense.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TaExpenseRequest(
    @NotNull LocalDate date,
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
    String petrolSlipPath,
    String photos
) {}
