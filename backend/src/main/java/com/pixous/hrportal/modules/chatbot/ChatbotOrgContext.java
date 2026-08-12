package com.pixous.hrportal.modules.chatbot;

import com.pixous.hrportal.modules.attendance.Attendance;
import com.pixous.hrportal.modules.attendance.AttendanceRepository;
import com.pixous.hrportal.modules.helpdesk.TicketRepository;
import com.pixous.hrportal.modules.leave.LeaveRequestRepository;
import com.pixous.hrportal.modules.org.Designation;
import com.pixous.hrportal.modules.org.DesignationRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a live, read-only snapshot of the organisation (headcount, today's
 * attendance, pending approvals, open tickets and team rosters) so the admin
 * chatbot can answer real questions with real numbers. Everything is wrapped
 * defensively — a data error never breaks the chat.
 */
@Component
@RequiredArgsConstructor
public class ChatbotOrgContext {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TicketRepository ticketRepository;
    private final DesignationRepository designationRepository;

    @Transactional(readOnly = true)
    public String snapshot() {
        try {
            LocalDate today = LocalDate.now();
            List<User> active = userRepository.findByEnabledTrue();
            int headcount = active.size();

            Set<Long> presentIds = attendanceRepository.findByWorkDate(today).stream()
                    .filter(a -> a.getPunchInAt() != null)
                    .map(Attendance::getUserId)
                    .collect(Collectors.toSet());
            long present = active.stream().filter(u -> presentIds.contains(u.getId())).count();
            long absent = Math.max(0, headcount - present);

            long pendingLeave = safeCount(() -> leaveRequestRepository.countByStatus("PENDING"));
            long openTickets = safeCount(() -> ticketRepository.countByStatusNot("CLOSED"));

            StringBuilder sb = new StringBuilder();
            sb.append("\n=== LIVE ORG DATA (as of ").append(today).append(") ===\n");
            sb.append("These are real, current numbers from the database. Use them verbatim when the ")
                    .append("user asks about current attendance, headcount, approvals, tickets or team members.\n");
            sb.append("Total active employees: ").append(headcount).append("\n");
            sb.append("Present today (punched in): ").append(present).append("\n");
            sb.append("Absent today (no punch-in): ").append(absent).append("\n");
            sb.append("Pending leave approvals: ").append(pendingLeave).append("\n");
            sb.append("Open tickets: ").append(openTickets).append("\n\n");

            Map<Long, List<User>> byDesignation = active.stream()
                    .filter(u -> u.getDesignationId() != null)
                    .collect(Collectors.groupingBy(User::getDesignationId));

            sb.append("TEAMS (a team = a designation; here are the members of each):\n");
            for (Designation d : designationRepository.findByActiveTrueOrderByNameAsc()) {
                List<User> members = byDesignation.getOrDefault(d.getId(), List.of());
                if (members.isEmpty()) continue;
                String names = members.stream()
                        .map(u -> u.getName() + " (" + (u.getEmployeeCode() == null ? "-" : u.getEmployeeCode()) + ")")
                        .collect(Collectors.joining(", "));
                sb.append("- ").append(d.getName()).append(" (").append(members.size())
                        .append(" member").append(members.size() == 1 ? "" : "s").append("): ")
                        .append(names).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private long safeCount(java.util.function.LongSupplier s) {
        try {
            return s.getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }
}
