package com.pixous.hrportal.modules.workreport;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.modules.workreport.dto.EmployeeWorkList;
import com.pixous.hrportal.modules.workreport.dto.WorkReportRequest;
import com.pixous.hrportal.modules.workreport.dto.WorkReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkReportService {

    private final WorkReportRepository repository;
    private final UserRepository userRepository;
    private final com.pixous.hrportal.modules.notification.OversightNotifier oversight;

    // ---------------- Employee (own rows) ----------------

    @Transactional(readOnly = true)
    public List<WorkReportResponse> mine(Long userId) {
        String name = userRepository.findById(userId).map(User::getName).orElse("?");
        String code = userRepository.findById(userId).map(User::getEmployeeCode).orElse("?");
        return repository.findByUserIdOrderByWorkDateDescIdDesc(userId).stream()
                .map(w -> WorkReportResponse.from(w, name, code))
                .toList();
    }

    @Transactional
    public WorkReportResponse create(Long userId, WorkReportRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        WorkReport w = new WorkReport();
        w.setUserId(userId);
        apply(w, req);
        WorkReport saved = repository.save(w);

        // HR, the administrators and the CTO. Work reports are the record of
        // what the company did with its day, and all three read them -- so
        // this is the one notification that goes wider than the CTO alone.
        oversight.notifyOversight(userId, "Work report logged",
                user.getName() + " logged " + saved.getWorkHours() + "h on "
                        + saved.getProjectName() + " (" + saved.getWorkDate() + ")",
                "WORK_REPORT", "/work-reports");

        return WorkReportResponse.from(saved, user.getName(), user.getEmployeeCode());
    }

    @Transactional
    public WorkReportResponse update(Long userId, Long id, WorkReportRequest req) {
        WorkReport w = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Work report"));
        if (!w.getUserId().equals(userId)) {
            throw ApiException.business("You can only edit your own work reports");
        }
        apply(w, req);
        w.setUpdatedAt(java.time.LocalDateTime.now());
        User user = userRepository.findById(userId).orElseThrow();
        return WorkReportResponse.from(w, user.getName(), user.getEmployeeCode());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        WorkReport w = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Work report"));
        if (!w.getUserId().equals(userId)) {
            throw ApiException.business("You can only delete your own work reports");
        }
        repository.delete(w);
    }

    // ---------------- Attachments ----------------

    /**
     * Adds files to one of the caller's own reports. Attachments accumulate, so
     * sending a second batch does not replace the first.
     */
    @Transactional
    public WorkReportResponse addAttachments(Long userId, Long id, List<String> paths) {
        WorkReport w = own(userId, id, "attach files to");
        if (paths == null || paths.isEmpty()) {
            throw ApiException.business("No files were received");
        }
        List<String> existing = attachmentList(w.getAttachments());
        paths.stream().filter(p -> p != null && !p.isBlank()).forEach(existing::add);
        w.setAttachments(String.join(",", existing));
        w.setUpdatedAt(java.time.LocalDateTime.now());
        User user = userRepository.findById(userId).orElseThrow();
        return WorkReportResponse.from(w, user.getName(), user.getEmployeeCode());
    }

    /** Removes one attachment from one of the caller's own reports. */
    @Transactional
    public WorkReportResponse removeAttachment(Long userId, Long id, String path) {
        WorkReport w = own(userId, id, "change the files on");
        List<String> remaining = attachmentList(w.getAttachments()).stream()
                .filter(p -> !p.equals(path))
                .collect(Collectors.toList());
        w.setAttachments(remaining.isEmpty() ? null : String.join(",", remaining));
        w.setUpdatedAt(java.time.LocalDateTime.now());
        User user = userRepository.findById(userId).orElseThrow();
        return WorkReportResponse.from(w, user.getName(), user.getEmployeeCode());
    }

    /** A report the caller owns, or a refusal naming what they were trying to do. */
    private WorkReport own(Long userId, Long id, String action) {
        WorkReport w = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Work report"));
        if (!w.getUserId().equals(userId)) {
            throw ApiException.business("You can only " + action + " your own work reports");
        }
        return w;
    }

    private static List<String> attachmentList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    // ---------------- HR / Admin (everyone, grouped, searchable) ----------------

    /**
     * Work reports from the caller's own team (same designation title) — what a
     * Team Leader sees. Never exposes another team's reports.
     */
    @Transactional(readOnly = true)
    public List<EmployeeWorkList> myTeam(Long userId, String q) {
        User me = userRepository.findById(userId).orElse(null);
        if (me == null) return List.of();
        String title = me.getDesignationTitle() == null ? "" : me.getDesignationTitle().trim();
        java.util.Set<Long> teamIds = title.isEmpty()
                ? java.util.Set.of(userId)
                : userRepository.findTeammatesByTitleOrDesignation(title, me.getDesignationId())
                        .stream().map(User::getId).collect(Collectors.toSet());

        return everyone(q).stream()
                .filter(g -> teamIds.contains(g.userId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeWorkList> everyone(String q) {
        List<WorkReport> all = repository.findAllByOrderByWorkDateDescIdDesc();
        Map<Long, User> users = userRepository.findAllById(
                        all.stream().map(WorkReport::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        // Preserve most-recent-first ordering while grouping by employee.
        Map<Long, List<WorkReport>> byUser = new LinkedHashMap<>();
        for (WorkReport w : all) {
            byUser.computeIfAbsent(w.getUserId(), k -> new ArrayList<>()).add(w);
        }

        String needle = q == null ? null : q.trim().toLowerCase();
        List<EmployeeWorkList> result = new ArrayList<>();
        for (Map.Entry<Long, List<WorkReport>> e : byUser.entrySet()) {
            User u = users.get(e.getKey());
            String name = u != null ? u.getName() : "?";
            String code = u != null ? u.getEmployeeCode() : "?";

            if (needle != null && !needle.isBlank()) {
                boolean match = name.toLowerCase().contains(needle)
                        || code.toLowerCase().contains(needle);
                if (!match) continue;
            }

            List<WorkReportResponse> rows = e.getValue().stream()
                    .map(w -> WorkReportResponse.from(w, name, code))
                    .toList();
            BigDecimal totalHours = e.getValue().stream()
                    .map(WorkReport::getWorkHours)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new EmployeeWorkList(e.getKey(), name, code,
                    rows.size(), totalHours, rows));
        }
        return result;
    }

    /** HR/Admin: export all work reports (optionally filtered by date range) to .xlsx. */
    @Transactional(readOnly = true)
    public byte[] exportExcel(java.time.LocalDate from, java.time.LocalDate to) {
        List<WorkReport> all = repository.findAllByOrderByWorkDateDescIdDesc();
        Map<Long, User> users = userRepository.findAllById(
                        all.stream().map(WorkReport::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Work Reports");
            String[] headers = {"Date", "Employee", "Employee Code", "Team", "Project", "Hours", "Task / Module"};
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            int r = 1;
            for (WorkReport w : all) {
                if (from != null && (w.getWorkDate() == null || w.getWorkDate().isBefore(from))) continue;
                if (to != null && (w.getWorkDate() == null || w.getWorkDate().isAfter(to))) continue;
                User u = users.get(w.getUserId());
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(w.getWorkDate() != null ? w.getWorkDate().toString() : "");
                row.createCell(1).setCellValue(u != null ? u.getName() : "?");
                row.createCell(2).setCellValue(u != null && u.getEmployeeCode() != null ? u.getEmployeeCode() : "");
                row.createCell(3).setCellValue(u != null && u.getDesignationTitle() != null ? u.getDesignationTitle() : "");
                row.createCell(4).setCellValue(w.getProjectName() != null ? w.getProjectName() : "");
                row.createCell(5).setCellValue(w.getWorkHours() != null ? w.getWorkHours().doubleValue() : 0d);
                row.createCell(6).setCellValue(w.getTaskDescription() != null ? w.getTaskDescription() : "");
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new ApiException(com.pixous.hrportal.common.ErrorCode.INTERNAL, "Failed to export work reports");
        }
    }

    // ---------------- helpers ----------------

    private void apply(WorkReport w, WorkReportRequest req) {
        w.setWorkDate(req.workDate());
        w.setProjectName(req.projectName());
        w.setWorkHours(req.workHours() == null ? BigDecimal.ZERO : req.workHours());
        w.setTaskDescription(req.taskDescription());
    }
}
