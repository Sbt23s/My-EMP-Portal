package com.pixous.hrportal.modules.workreport.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * All work-report rows for one employee, for the HR/Admin "Employee Work List"
 * section (click an employee to see their rows; searchable by name/code).
 */
public record EmployeeWorkList(
        Long userId,
        String employeeName,
        String employeeCode,
        int totalRows,
        BigDecimal totalHours,
        List<WorkReportResponse> rows
) {}
