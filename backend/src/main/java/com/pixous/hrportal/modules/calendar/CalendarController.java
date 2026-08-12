package com.pixous.hrportal.modules.calendar;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService service;

    /**
     * Birthdays, work anniversaries and company events touching the range —
     * everything the calendar draws besides holidays, leave and task due dates,
     * which the page already reads from their own modules.
     */
    @GetMapping("/events")
    public ApiResponse<List<CalendarDTOs.CalendarEvent>> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.events(from, to, SecurityUtils.currentUserId()));
    }

    @PostMapping("/events")
    @PreAuthorize("hasAnyAuthority('ORG_MANAGE','USER_MANAGE')")
    public ApiResponse<CalendarDTOs.CalendarEvent> create(
            @RequestBody CalendarDTOs.EventRequest req) {
        return ApiResponse.ok(service.create(req, SecurityUtils.currentUserId()), "Event added");
    }

    @PutMapping("/events/{id}")
    @PreAuthorize("hasAnyAuthority('ORG_MANAGE','USER_MANAGE')")
    public ApiResponse<CalendarDTOs.CalendarEvent> update(
            @PathVariable Long id, @RequestBody CalendarDTOs.EventRequest req) {
        return ApiResponse.ok(service.update(id, req), "Event updated");
    }

    @DeleteMapping("/events/{id}")
    @PreAuthorize("hasAnyAuthority('ORG_MANAGE','USER_MANAGE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.message("Event removed");
    }
}
