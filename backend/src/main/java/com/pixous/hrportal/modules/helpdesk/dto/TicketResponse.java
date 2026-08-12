package com.pixous.hrportal.modules.helpdesk.dto;

import com.pixous.hrportal.modules.helpdesk.Ticket;

import java.time.LocalDateTime;
import java.util.List;

public record TicketResponse(
        Long id, String ticketCode, Long raisedBy, String raisedByName, String raisedByCode, String title,
        String description, String attachments,
        String type, String category, String priority, String status,
        Long assignedTo, String assignedToName, LocalDateTime slaDueAt, Integer rating,
        LocalDateTime resolvedAt, LocalDateTime createdAt, List<CommentResponse> comments
) {
    public static TicketResponse from(Ticket t, String raisedByName, String raisedByCode,
                                      String assignedToName, List<CommentResponse> comments) {
        return new TicketResponse(t.getId(), t.getTicketCode(), t.getRaisedBy(), raisedByName, raisedByCode,
                t.getTitle(), t.getDescription(), t.getAttachments(),
                t.getType(), t.getCategory(), t.getPriority(),
                t.getStatus(), t.getAssignedTo(), assignedToName, t.getSlaDueAt(), t.getRating(),
                t.getResolvedAt(), t.getCreatedAt(), comments);
    }
}
