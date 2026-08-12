package com.pixous.hrportal.modules.calendar;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Everything the company puts on a calendar, in one list.
 *
 * <p>Two of the kinds are not stored anywhere: a birthday and a work anniversary
 * are worked out from the employee record for the range being looked at, so they
 * need no yearly upkeep and cannot drift out of step with the profile.
 *
 * <p>Holidays are deliberately left out. The page already reads them, and it has
 * to keep reading them separately, because a holiday is what makes a day
 * non-working — merging it in here would blur a distinction payroll depends on.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final Set<String> TYPES =
            Set.of("CELEBRATION", "MEETING", "TRAINING", "OTHER");

    private final CompanyEventRepository eventRepository;
    private final UserRepository userRepository;

    // ---- reading ----

    /**
     * Birthdays, work anniversaries and company events touching the range.
     *
     * <p>An event addressed to one team is shown to that team and to whoever runs
     * the portal; a training session for the Civil site has no business filling
     * everybody else's month.
     */
    @Transactional(readOnly = true)
    public List<CalendarDTOs.CalendarEvent> events(LocalDate from, LocalDate to, Long requesterId) {
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.business("Give a from date and a to date, in that order.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > 400) {
            throw ApiException.business("Ask for a year at a time or less.");
        }

        List<CalendarDTOs.CalendarEvent> out = new ArrayList<>();

        // ---- birthdays and anniversaries, from the employee records ----
        for (User u : userRepository.findByEnabledTrue()) {
            if ("OFFBOARDED".equalsIgnoreCase(String.valueOf(u.getProfileStatus()))) continue;
            addRecurring(out, u, u.getDob(), "BIRTHDAY", from, to);
            addRecurring(out, u, u.getDateOfJoining(), "ANNIVERSARY", from, to);
        }

        // ---- company events ----
        String myTeam = null;
        boolean privileged = SecurityUtils.hasAuthority("ORG_MANAGE")
                || SecurityUtils.hasAuthority("USER_MANAGE");
        if (!privileged && requesterId != null) {
            myTeam = userRepository.findById(requesterId)
                    .map(User::getDesignationTitle).orElse(null);
        }
        for (CompanyEvent e : eventRepository.findTouching(from, to)) {
            if (!privileged && e.getAudienceTeam() != null && !e.getAudienceTeam().isBlank()) {
                if (myTeam == null || !e.getAudienceTeam().trim().equalsIgnoreCase(myTeam.trim())) {
                    continue;
                }
            }
            out.add(toEvent(e));
        }

        out.sort(java.util.Comparator
                .comparing(CalendarDTOs.CalendarEvent::date)
                .thenComparing(c -> c.startTime() == null ? "" : c.startTime())
                .thenComparing(CalendarDTOs.CalendarEvent::title,
                        java.util.Comparator.nullsLast(String::compareToIgnoreCase)));
        return out;
    }

    /**
     * Every occurrence of a yearly date that falls inside the range. A range may
     * span a new year, so more than one occurrence can land in it.
     */
    private void addRecurring(List<CalendarDTOs.CalendarEvent> out, User u, LocalDate base,
                              String type, LocalDate from, LocalDate to) {
        if (base == null) return;
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            LocalDate when = onYear(base, year);
            if (when == null || when.isBefore(from) || when.isAfter(to)) continue;

            Integer years = null;
            if ("ANNIVERSARY".equals(type)) {
                int completed = when.getYear() - base.getYear();
                // The joining day itself is not an anniversary.
                if (completed < 1) continue;
                years = completed;
            }
            out.add(new CalendarDTOs.CalendarEvent(
                    null, type,
                    "BIRTHDAY".equals(type)
                            ? u.getName() + "'s birthday"
                            : u.getName() + " · " + years + (years == 1 ? " year" : " years"),
                    null, when, null, null, null, null, null,
                    u.getId(), u.getName(), u.getEmployeeCode(),
                    u.getDesignationTitle(), u.getPhotoPath(), years));
        }
    }

    /** The same day in another year; 29 February lands on 1 March in a common year. */
    private static LocalDate onYear(LocalDate base, int year) {
        try {
            return base.withYear(year);
        } catch (Exception e) {
            return LocalDate.of(year, 3, 1);
        }
    }

    // ---- writing ----

    @Transactional
    public CalendarDTOs.CalendarEvent create(CalendarDTOs.EventRequest req, Long actorId) {
        CompanyEvent e = new CompanyEvent();
        apply(e, req);
        e.setCreatedBy(actorId);
        return toEvent(eventRepository.save(e));
    }

    @Transactional
    public CalendarDTOs.CalendarEvent update(Long id, CalendarDTOs.EventRequest req) {
        CompanyEvent e = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event"));
        apply(e, req);
        return toEvent(eventRepository.save(e));
    }

    @Transactional
    public void delete(Long id) {
        CompanyEvent e = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event"));
        eventRepository.delete(e);
    }

    private void apply(CompanyEvent e, CalendarDTOs.EventRequest req) {
        String title = req.title() == null ? "" : req.title().trim();
        if (title.isEmpty()) throw ApiException.business("Give the event a name.");
        if (req.eventDate() == null) throw ApiException.business("Pick a date for the event.");

        String type = req.eventType() == null ? "" : req.eventType().trim().toUpperCase();
        if (!TYPES.contains(type)) type = "OTHER";

        LocalDate end = req.endDate();
        if (end != null && end.isBefore(req.eventDate())) {
            throw ApiException.business("The last day cannot be before the first.");
        }
        // A single-day event needs no end date; storing one is only noise.
        if (end != null && end.equals(req.eventDate())) end = null;

        LocalTime start = parseTime(req.startTime(), "start");
        LocalTime finish = parseTime(req.endTime(), "end");
        if (start != null && finish != null && finish.isBefore(start) && end == null) {
            throw ApiException.business("The end time is before the start time.");
        }

        e.setTitle(title);
        e.setDescription(blankToNull(req.description()));
        e.setEventType(type);
        e.setEventDate(req.eventDate());
        e.setEndDate(end);
        e.setStartTime(start);
        e.setEndTime(finish);
        e.setLocation(blankToNull(req.location()));
        e.setAudienceTeam(blankToNull(req.audienceTeam()));
    }

    private static LocalTime parseTime(String raw, String which) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalTime.parse(raw.trim().length() == 5 ? raw.trim() : raw.trim().substring(0, 5));
        } catch (Exception e) {
            throw ApiException.business("Give the " + which + " time as HH:mm.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static CalendarDTOs.CalendarEvent toEvent(CompanyEvent e) {
        return new CalendarDTOs.CalendarEvent(
                e.getId(), e.getEventType(), e.getTitle(), e.getDescription(),
                e.getEventDate(), e.getEndDate(),
                e.getStartTime() == null ? null : e.getStartTime().toString(),
                e.getEndTime() == null ? null : e.getEndTime().toString(),
                e.getLocation(), e.getAudienceTeam(),
                null, null, null, null, null, null);
    }
}
