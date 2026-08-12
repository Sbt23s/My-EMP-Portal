package com.pixous.hrportal.modules.payroll;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAuthority('REPORT_VIEW')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/attendance")
    public ResponseEntity<ByteArrayResource> attendanceReport(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "xlsx") String format) {
        
        byte[] bytes = reportService.generateAttendanceReport(from, to, deptId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance_report.xlsx")
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/leave")
    public ResponseEntity<ByteArrayResource> leaveReport(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "xlsx") String format) {
        
        byte[] bytes = reportService.generateLeaveReport(from, to, deptId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leave_report.xlsx")
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/payroll")
    public ResponseEntity<ByteArrayResource> payrollReport(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(defaultValue = "xlsx") String format) {
        
        byte[] bytes = reportService.generatePayrollReport(month, year);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payroll_report.xlsx")
                .body(new ByteArrayResource(bytes));
    }
}
