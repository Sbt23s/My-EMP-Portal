package com.pixous.hrportal.modules.chatbot;

import com.pixous.hrportal.modules.attendance.Attendance;
import com.pixous.hrportal.modules.attendance.AttendanceRepository;
import com.pixous.hrportal.modules.complaint.ComplaintNeedRepository;
import com.pixous.hrportal.modules.helpdesk.TicketRepository;
import com.pixous.hrportal.modules.leave.LeaveBalanceRepository;
import com.pixous.hrportal.modules.leave.LeaveRequestRepository;
import com.pixous.hrportal.modules.org.DesignationRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The assistant's answers for administrators when no language model is reachable.
 *
 * These are the questions a CTO actually asks, and the answer has to come from
 * the database rather than from a fixed apology. The cases below are the ones
 * that were wrong in production: "which absent today" returned "the AI service
 * is temporarily unavailable" while the number sat one query away.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatbotOrgContextTest {

    @Mock private UserRepository userRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private ComplaintNeedRepository complaintRepository;
    @Mock private LeaveBalanceRepository leaveBalanceRepository;

    private ChatbotOrgContext context;

    @BeforeEach
    void setUp() {
        context = new ChatbotOrgContext(
                userRepository, attendanceRepository, leaveRequestRepository,
                ticketRepository, designationRepository, complaintRepository,
                leaveBalanceRepository);

        // Three people on the books; one of them punched in today.
        User present = user(1L, "Amutha Kumari G", "PIX-E039", "Mobile Developer");
        User absentOne = user(2L, "Bharath kumar Murugesan", "PIX-E027", "UI/UX Designer");
        User absentTwo = user(3L, "Harish C", "PIX-E055", "Software Engineer");
        when(userRepository.findByEnabledTrue()).thenReturn(List.of(present, absentOne, absentTwo));

        Attendance punched = new Attendance();
        punched.setUserId(1L);
        punched.setWorkDate(LocalDate.now());
        punched.setPunchInAt(LocalDateTime.now());
        when(attendanceRepository.findByWorkDate(any())).thenReturn(List.of(punched));

        when(designationRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());
        when(attendanceRepository.findByUserIdAndWorkDate(any(), any()))
                .thenReturn(java.util.Optional.empty());
        when(attendanceRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(leaveRequestRepository.findByUserIdOrderByCreatedAtDesc(any(Long.class)))
                .thenReturn(List.of());
        when(leaveBalanceRepository.findByUserId(any())).thenReturn(List.of());
        when(ticketRepository.findAll()).thenReturn(List.of());
        when(complaintRepository.findAll()).thenReturn(List.of());
    }

    private User user(Long id, String name, String code, String designation) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setEmployeeCode(code);
        u.setDesignationTitle(designation);
        u.setEnabled(true);
        return u;
    }

    @Test
    void answersWhoIsAbsentWithRealNamesAndCounts() {
        String answer = context.directAnswer("which absent today", "en");

        assertThat(answer).isNotNull();
        assertThat(answer).contains("Absent today: 2 of 3");
        assertThat(answer).contains("Bharath kumar Murugesan");
        assertThat(answer).contains("Harish C");
        // The one who punched in is not on the list.
        assertThat(answer).doesNotContain("Amutha Kumari G");
    }

    @Test
    void answersWhoIsPresent() {
        String answer = context.directAnswer("who is present today", "en");

        assertThat(answer).isNotNull();
        assertThat(answer).contains("Present today: 1 of 3");
        assertThat(answer).contains("Amutha Kumari G");
    }

    @Test
    void answersHeadcount() {
        assertThat(context.directAnswer("how many employees do we have", "en"))
                .contains("Active employees: 3");
    }

    @Test
    void answersPendingLeave() {
        when(leaveRequestRepository.countByStatus("PENDING")).thenReturn(4L);

        assertThat(context.directAnswer("how many leave requests are pending approval", "en"))
                .contains("4 leave requests awaiting approval");
    }

    @Test
    void reportsNothingPendingRatherThanZero() {
        when(leaveRequestRepository.countByStatus("PENDING")).thenReturn(0L);

        assertThat(context.directAnswer("any pending leave approvals", "en"))
                .contains("All caught up");
    }

    @Test
    void answersOpenTickets() {
        when(ticketRepository.countByStatusNot("CLOSED")).thenReturn(2L);

        assertThat(context.directAnswer("show open support tickets", "en"))
                .contains("2 open support tickets");
    }

    @Test
    void aNamedEmployeeOutranksTheTopic() {
        // Mentions "absent", but it is really a question about one person, so
        // the answer must be that person's record rather than the absence list.
        String answer = context.directAnswer("is Harish absent today", "en");

        assertThat(answer).isNotNull();
        assertThat(answer).contains("Harish C");
        assertThat(answer).contains("PIX-E055");
        assertThat(answer).doesNotContain("Absent today: 2 of 3");
    }

    @Test
    void answersInTamilWhenAsked() {
        String answer = context.directAnswer("who is absent", "ta");

        assertThat(answer).isNotNull();
        // The count and the names carry over; the wording is Tamil.
        assertThat(answer).contains("3");
        assertThat(answer).contains("Harish C");
    }

    @Test
    void returnsNullForAQuestionItCannotAnswer() {
        // Must be null, not a guess: the caller falls through to the ordinary
        // reply instead of confidently answering something else.
        assertThat(context.directAnswer("what is our maternity leave policy", "en")).isNull();
        assertThat(context.directAnswer("", "en")).isNull();
        assertThat(context.directAnswer(null, "en")).isNull();
    }

    @Test
    void survivesADatabaseFailure() {
        when(userRepository.findByEnabledTrue()).thenThrow(new RuntimeException("db down"));

        // No exception escapes; the chat keeps working without this answer.
        assertThat(context.directAnswer("who is absent today", "en")).isNull();
    }
}
