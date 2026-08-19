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
    private final com.pixous.hrportal.modules.complaint.ComplaintNeedRepository complaintRepository;
    private final com.pixous.hrportal.modules.leave.LeaveBalanceRepository leaveBalanceRepository;

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

    /**
     * Everything about the specific people a question names.
     *
     * The org snapshot above answers "how many"; this answers "how is Priya
     * doing". Both are needed, and they cannot be the same thing: putting every
     * employee's leave, attendance, tickets and complaints into the prompt would
     * be a wall of text for thirty-three people on every message, most of it
     * about people nobody asked about, and it would push the actual question out
     * of the model's context long before the company grew.
     *
     * So this reads the question first and attaches only the people it mentions.
     * Nothing is added when no one is named.
     *
     * <p>Only ever called for HR and administrators. An employee asking about a
     * colleague gets the ordinary assistant, because one person's leave history
     * is not another person's business.
     */
    @Transactional(readOnly = true)
    public String peopleMentioned(String question) {
        if (question == null || question.isBlank()) return "";

        try {
            String q = question.toLowerCase();
            List<User> active = userRepository.findByEnabledTrue();

            List<User> named = active.stream()
                    .filter(u -> mentions(q, u))
                    // Three at most. A question naming half the company is
                    // really a question about the company, and the snapshot
                    // above already answers that.
                    .limit(3)
                    .toList();

            if (named.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("\n=== THE PEOPLE THIS QUESTION NAMES ===\n");
            sb.append("Real records, current as of ").append(LocalDate.now())
              .append(". Answer from these rather than generalities.\n\n");

            for (User u : named) {
                sb.append(personDetail(u));
            }
            return sb.toString();
        } catch (Exception e) {
            // The assistant still works without this; a lookup failure must not
            // turn a question into an error message.
            return "";
        }
    }

    /**
     * Whether the question is about this person.
     *
     * Matched on the employee code, the full name, and each part of the name of
     * three letters or more. The last is what makes "how many leaves has Priya
     * taken" work, since nobody types a colleague's full legal name into a chat
     * box. Two-letter fragments are skipped: an initial would match most of the
     * company.
     */
    private boolean mentions(String lowerQuestion, User u) {
        String code = u.getEmployeeCode();
        if (code != null && !code.isBlank() && lowerQuestion.contains(code.toLowerCase())) {
            return true;
        }
        String name = u.getName();
        if (name == null || name.isBlank()) return false;

        String lower = name.toLowerCase();
        if (lowerQuestion.contains(lower)) return true;

        for (String part : lower.split("\\s+")) {
            if (part.length() >= 3 && lowerQuestion.contains(part)) return true;
        }
        return false;
    }

    private String personDetail(User u) {
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        sb.append("PERSON: ").append(u.getName())
          .append(" (").append(u.getEmployeeCode() == null ? "no code" : u.getEmployeeCode()).append(")\n");
        if (u.getDesignationTitle() != null && !u.getDesignationTitle().isBlank()) {
            sb.append("  Role/team: ").append(u.getDesignationTitle()).append("\n");
        }
        if (u.getEmail() != null) sb.append("  Email: ").append(u.getEmail()).append("\n");
        if (u.getPhone() != null) sb.append("  Phone: ").append(u.getPhone()).append("\n");
        sb.append("  Status: ").append(u.getProfileStatus() == null ? "ACTIVE" : u.getProfileStatus()).append("\n");

        // ---- attendance -------------------------------------------------
        try {
            var todayRow = attendanceRepository.findByUserIdAndWorkDate(u.getId(), today);
            boolean inToday = todayRow.isPresent() && todayRow.get().getPunchInAt() != null;
            sb.append("  Today: ").append(inToday
                    ? "punched in at " + todayRow.get().getPunchInAt()
                    : "no punch-in recorded").append("\n");

            long presentThisMonth = attendanceRepository
                    .findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(u.getId(), monthStart, today)
                    .stream().filter(a -> a.getPunchInAt() != null).count();
            sb.append("  Days present this month: ").append(presentThisMonth).append("\n");
        } catch (Exception ignored) {
            // An attendance table that cannot be read should not remove the
            // name, the role and the contact details from the answer.
        }

        // ---- leave --------------------------------------------------------
        try {
            var requests = leaveRequestRepository.findByUserIdOrderByCreatedAtDesc(u.getId());
            long pending = requests.stream().filter(r -> "PENDING".equalsIgnoreCase(r.getStatus())).count();
            long approved = requests.stream().filter(r -> "APPROVED".equalsIgnoreCase(r.getStatus())).count();
            sb.append("  Leave requests: ").append(requests.size())
              .append(" total, ").append(pending).append(" pending, ")
              .append(approved).append(" approved\n");

            requests.stream().limit(3).forEach(r ->
                    sb.append("    - ").append(r.getFromDate()).append(" to ").append(r.getToDate())
                      .append(" (").append(r.getStatus()).append(")")
                      .append(r.getReason() == null ? "" : " - " + r.getReason())
                      .append("\n"));

            leaveBalanceRepository.findByUserId(u.getId()).forEach(b ->
                    sb.append("    balance: type ").append(b.getLeaveTypeId())
                      .append(" - allocated ").append(b.getAllocated())
                      .append(", used ").append(b.getUsed()).append("\n"));
        } catch (Exception ignored) {
        }

        // ---- tickets and complaints ---------------------------------------
        try {
            long openTickets = ticketRepository.findAll().stream()
                    .filter(t -> t.getRaisedBy() != null && t.getRaisedBy().equals(u.getId()))
                    .filter(t -> !"CLOSED".equalsIgnoreCase(t.getStatus()))
                    .count();
            sb.append("  Open support tickets: ").append(openTickets).append("\n");
        } catch (Exception ignored) {
        }

        try {
            long complaints = complaintRepository.findAll().stream()
                    .filter(c -> c.getRaisedBy() != null && c.getRaisedBy().equals(u.getId()))
                    .count();
            sb.append("  Complaints raised: ").append(complaints).append("\n");
        } catch (Exception ignored) {
        }

        sb.append("\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Answering without a language model
    //
    // With no LLM key configured the assistant replied with one fixed sentence
    // -- "ask me about leave, attendance, payslips or assets (the AI service is
    // temporarily unavailable)" -- to a CTO who had just asked which employees
    // were absent today. That number was sitting in the database the whole time.
    //
    // The questions an administrator actually asks are therefore answered here,
    // from live data, whether or not a model is reachable. With a key configured
    // the model still answers everything and phrases it freely; this is the
    // floor, not a replacement for it.
    //
    // Read-only throughout, and only ever reached for HR and administrators.
    // ------------------------------------------------------------------

    /**
     * A real answer to an administrator's question, or null when the question is
     * not one these rules cover.
     *
     * Returning null is the important part: it lets the caller fall through to
     * the ordinary reply instead of confidently answering a different question.
     */
    @Transactional(readOnly = true)
    public String directAnswer(String question, String lang) {
        if (question == null || question.isBlank()) return null;
        String q = question.toLowerCase();
        boolean ta = "ta".equals(lang);

        try {
            // A named person outranks every topic below: "is Priya in today" is
            // a question about Priya, not about attendance in general.
            String person = peopleMentioned(question);
            if (person != null && !person.isBlank()) {
                return (ta ? "இவர்களைப் பற்றிய தற்போதைய தகவல்:\n\n"
                           : "Here are their current records:\n\n") + person.trim();
            }

            if (asksAbsent(q)) return absentAnswer(ta);
            if (asksPresent(q)) return presentAnswer(ta);
            if (asksHeadcount(q)) return headcountAnswer(ta);
            if (asksPendingLeave(q)) return pendingLeaveAnswer(ta);
            if (asksTickets(q)) return ticketAnswer(ta);
            if (asksComplaints(q)) return complaintAnswer(ta);
            if (asksTeams(q)) return teamAnswer(ta);
            return null;
        } catch (Exception e) {
            // A data error must leave the chat working, not turn a question
            // into an error message.
            return null;
        }
    }

    private boolean asksAbsent(String q) {
        return q.contains("absent") || q.contains("not present") || q.contains("who is out")
                || q.contains("வரவில்லை") || q.contains("இல்லாத") || q.contains("வராத");
    }

    private boolean asksPresent(String q) {
        return q.contains("present") || q.contains("punched in") || q.contains("who is in")
                || q.contains("attendance today")
                || q.contains("வந்த") || q.contains("வருகை");
    }

    private boolean asksHeadcount(String q) {
        return q.contains("how many employee") || q.contains("headcount")
                || q.contains("total employee") || q.contains("staff strength")
                || q.contains("மொத்த ஊழிய");
    }

    private boolean asksPendingLeave(String q) {
        return (q.contains("leave") || q.contains("விடுப்ப"))
                && (q.contains("pending") || q.contains("approv") || q.contains("நிலுவை"));
    }

    private boolean asksTickets(String q) {
        return q.contains("ticket") || q.contains("support") || q.contains("helpdesk")
                || q.contains("ஆதரவு");
    }

    private boolean asksComplaints(String q) {
        return q.contains("complaint") || q.contains("குறை");
    }

    private boolean asksTeams(String q) {
        return q.contains("team") || q.contains("designation") || q.contains("குழு");
    }

    private Set<Long> presentIdsToday() {
        return attendanceRepository.findByWorkDate(LocalDate.now()).stream()
                .filter(a -> a.getPunchInAt() != null)
                .map(Attendance::getUserId)
                .collect(Collectors.toSet());
    }

    /**
     * Names, capped.
     *
     * The count is the answer; the names are the detail. A whole company marked
     * absent on a public holiday would otherwise return thirty-odd lines into a
     * chat bubble.
     */
    private void appendNames(StringBuilder sb, List<User> people, boolean ta, boolean withTeam) {
        people.stream().limit(25).forEach(u -> {
            sb.append("\n- ").append(u.getName());
            if (u.getEmployeeCode() != null) sb.append(" (").append(u.getEmployeeCode()).append(")");
            if (withTeam && u.getDesignationTitle() != null && !u.getDesignationTitle().isBlank()) {
                sb.append(" - ").append(u.getDesignationTitle());
            }
        });
        if (people.size() > 25) {
            int rest = people.size() - 25;
            sb.append(ta ? "\n\n...மற்றும் " + rest + " பேர்." : "\n\n...and " + rest + " more.");
        }
    }

    private String absentAnswer(boolean ta) {
        List<User> active = userRepository.findByEnabledTrue();
        Set<Long> present = presentIdsToday();
        List<User> absent = active.stream().filter(u -> !present.contains(u.getId())).toList();

        if (absent.isEmpty()) {
            return ta ? "இன்று அனைவரும் வருகை பதிவு செய்துள்ளனர்." : "Everyone has punched in today.";
        }

        StringBuilder sb = new StringBuilder(ta
                ? "இன்று " + absent.size() + " பேர் வரவில்லை (மொத்தம் " + active.size() + "):"
                : "Absent today: " + absent.size() + " of " + active.size() + ".");
        appendNames(sb, absent, ta, true);
        sb.append(ta ? "\n\n(முழு விவரம் Employee Attendance பக்கத்தில்.)"
                     : "\n\n(Full detail on the Employee Attendance page.)");
        return sb.toString();
    }

    private String presentAnswer(boolean ta) {
        List<User> active = userRepository.findByEnabledTrue();
        Set<Long> present = presentIdsToday();
        List<User> in = active.stream().filter(u -> present.contains(u.getId())).toList();

        if (in.isEmpty()) {
            return ta ? "இன்று இதுவரை யாரும் வருகை பதிவு செய்யவில்லை."
                      : "Nobody has punched in yet today.";
        }
        StringBuilder sb = new StringBuilder(ta
                ? "இன்று " + in.size() + "/" + active.size() + " பேர் வந்துள்ளனர்:"
                : "Present today: " + in.size() + " of " + active.size() + ".");
        appendNames(sb, in, ta, false);
        return sb.toString();
    }

    private String headcountAnswer(boolean ta) {
        List<User> active = userRepository.findByEnabledTrue();
        Set<Long> present = presentIdsToday();
        long in = active.stream().filter(u -> present.contains(u.getId())).count();
        return ta
                ? "மொத்த செயல்படும் ஊழியர்கள்: " + active.size() + ". இன்று வந்தவர்கள்: " + in + "."
                : "Active employees: " + active.size() + ". Present today: " + in + ".";
    }

    private String pendingLeaveAnswer(boolean ta) {
        long pending = safeCount(() -> leaveRequestRepository.countByStatus("PENDING"));
        if (pending == 0) {
            return ta ? "நிலுவையில் விடுப்பு விண்ணப்பங்கள் இல்லை."
                      : "No leave requests are pending. All caught up.";
        }
        return ta
                ? pending + " விடுப்பு விண்ணப்பம் அனுமதிக்காக காத்திருக்கிறது. (Leave Management > Approvals)"
                : pending + " leave request" + (pending == 1 ? "" : "s")
                    + " awaiting approval. See Leave Management > Approvals.";
    }

    private String ticketAnswer(boolean ta) {
        long open = safeCount(() -> ticketRepository.countByStatusNot("CLOSED"));
        if (open == 0) {
            return ta ? "திறந்த ஆதரவு கோரிக்கைகள் இல்லை." : "No open support tickets.";
        }
        return ta
                ? open + " திறந்த ஆதரவு கோரிக்கை உள்ளது. (Supports பக்கம்)"
                : open + " open support ticket" + (open == 1 ? "" : "s") + ". See the Supports page.";
    }

    private String complaintAnswer(boolean ta) {
        long total = safeCount(() -> (long) complaintRepository.findAll().size());
        if (total == 0) {
            return ta ? "இதுவரை குறைகள் பதிவாகவில்லை." : "No complaints have been raised.";
        }
        return ta
                ? total + " குறை பதிவாகியுள்ளது. (Complaints பக்கம்)"
                : total + " complaint" + (total == 1 ? "" : "s") + " on record. See the Complaints page.";
    }

    private String teamAnswer(boolean ta) {
        List<User> active = userRepository.findByEnabledTrue();
        Map<Long, List<User>> byDesignation = active.stream()
                .filter(u -> u.getDesignationId() != null)
                .collect(Collectors.groupingBy(User::getDesignationId));

        StringBuilder sb = new StringBuilder();
        for (Designation d : designationRepository.findByActiveTrueOrderByNameAsc()) {
            List<User> members = byDesignation.getOrDefault(d.getId(), List.of());
            if (members.isEmpty()) continue;
            sb.append("\n- ").append(d.getName()).append(" (").append(members.size()).append("): ")
              .append(members.stream().map(User::getName).collect(Collectors.joining(", ")));
        }
        // No teams recorded is not an answer; let the caller fall through.
        if (sb.length() == 0) return null;
        return (ta ? "குழுக்கள்:" : "Teams:") + sb;
    }
}
