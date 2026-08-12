package com.pixous.hrportal.modules.workreport;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.workreport.dto.EmployeeWorkList;
import com.pixous.hrportal.modules.workreport.dto.WorkReportRequest;
import com.pixous.hrportal.modules.workreport.dto.WorkReportResponse;
import com.pixous.hrportal.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-reports")
@RequiredArgsConstructor
public class WorkReportController {

    private final WorkReportService service;
    private final com.pixous.hrportal.common.StorageService storageService;
    private final WorkReportReminderService reminderService;

    // ---- Employee: own rows ----

    @GetMapping("/me")
    public ApiResponse<List<WorkReportResponse>> mine() {
        return ApiResponse.ok(service.mine(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public ApiResponse<WorkReportResponse> create(@Valid @RequestBody WorkReportRequest req) {
        return ApiResponse.ok(service.create(SecurityUtils.currentUserId(), req), "Work report saved");
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkReportResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody WorkReportRequest req) {
        return ApiResponse.ok(service.update(SecurityUtils.currentUserId(), id, req), "Work report updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(SecurityUtils.currentUserId(), id);
        return ApiResponse.message("Work report deleted");
    }

    // ---- Attachments on a report: screenshots, documents, sheets, video ----

    @PostMapping("/{id}/attachments")
    public ApiResponse<WorkReportResponse> addAttachments(
            @PathVariable Long id,
            @RequestParam("files") org.springframework.web.multipart.MultipartFile[] files) {
        List<String> paths = new java.util.ArrayList<>();
        for (org.springframework.web.multipart.MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                paths.add(storageService.store(file, "work-reports"));
            }
        }
        return ApiResponse.ok(
                service.addAttachments(SecurityUtils.currentUserId(), id, paths),
                paths.size() == 1 ? "File attached" : paths.size() + " files attached");
    }

    @DeleteMapping("/{id}/attachments")
    public ApiResponse<WorkReportResponse> removeAttachment(@PathVariable Long id,
                                                            @RequestParam String path) {
        return ApiResponse.ok(
                service.removeAttachment(SecurityUtils.currentUserId(), id, path),
                "File removed");
    }

    // ---- HR / Admin: everyone grouped by employee, searchable ----

    /** Work reports from the caller's own team — what a Team Leader sees. */
    @GetMapping("/team")
    public ApiResponse<List<EmployeeWorkList>> myTeam(@RequestParam(required = false) String q) {
        return ApiResponse.ok(service.myTeam(SecurityUtils.currentUserId(), q));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('REPORT_VIEW','USER_MANAGE')")
    public ApiResponse<List<EmployeeWorkList>> everyone(@RequestParam(required = false) String q) {
        return ApiResponse.ok(service.everyone(q));
    }

    // ---- The daily reminder for a report nobody has filed ----

    /** Who has not filed for a day. HR and the admin chase; a TL sees their team. */
    @GetMapping("/reminder/pending")
    @PreAuthorize("hasAnyAuthority('REPORT_VIEW','USER_MANAGE')")
    public ApiResponse<java.util.Map<String, Object>> reminderPending(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        return ApiResponse.ok(reminderService.pendingToday(date));
    }

    @GetMapping("/reminder/settings")
    @PreAuthorize("hasAnyAuthority('REPORT_VIEW','USER_MANAGE')")
    public ApiResponse<java.util.Map<String, Object>> reminderSettings() {
        return ApiResponse.ok(reminderService.settings());
    }

    @PutMapping("/reminder/settings")
    @PreAuthorize("hasAnyAuthority('REPORT_VIEW','USER_MANAGE')")
    public ApiResponse<java.util.Map<String, Object>> saveReminderSettings(
            @RequestBody java.util.Map<String, Object> body) {
        boolean on = !Boolean.FALSE.equals(body.get("enabled"));
        String time = body.get("time") == null ? "18:30" : String.valueOf(body.get("time"));
        reminderService.saveSettings(on, time);
        return ApiResponse.ok(reminderService.settings(), "Reminder settings saved");
    }

    /** Chase everybody who has not filed, right now, without waiting for the hour. */
    @PostMapping("/reminder/send")
    @PreAuthorize("hasAnyAuthority('REPORT_VIEW','USER_MANAGE')")
    public ApiResponse<java.util.Map<String, Object>> sendReminders(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        int sent = reminderService.remindNow(date);
        return ApiResponse.ok(java.util.Map.of("sent", sent),
                sent == 0 ? "Everybody has filed their report" : "Reminded " + sent + " employee(s)");
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('REPORT_VIEW','USER_MANAGE')")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.ByteArrayResource> export(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        byte[] bytes = service.exportExcel(from, to);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=work-reports.xlsx")
                .body(new org.springframework.core.io.ByteArrayResource(bytes));
    }
}
