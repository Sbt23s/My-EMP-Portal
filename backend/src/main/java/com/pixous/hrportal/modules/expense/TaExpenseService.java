package com.pixous.hrportal.modules.expense;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.modules.expense.dto.TaExpenseRequest;
import com.pixous.hrportal.modules.expense.dto.TaExpenseResponse;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaExpenseService {

    private final TaExpenseRepository taExpenseRepository;
    private final UserRepository userRepository;
    private final com.pixous.hrportal.modules.notification.NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;

    public TaExpenseService(TaExpenseRepository taExpenseRepository,
                            UserRepository userRepository,
                            com.pixous.hrportal.modules.notification.NotificationService notificationService,
                            com.pixous.hrportal.common.SmsService smsService) {
        this.taExpenseRepository = taExpenseRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.smsService = smsService;
    }

    @Transactional
    public TaExpenseResponse createTaExpense(Long userId, TaExpenseRequest req) {
        TaExpense expense = new TaExpense();
        expense.setUserId(userId);
        expense.setDate(req.date());
        expense.setLocation(req.location());
        expense.setCategory(req.category());
        expense.setStartingKm(req.startingKm());
        expense.setEndingKm(req.endingKm());
        expense.setTotalKm(req.totalKm());
        expense.setHillsKm(req.hillsKm());
        expense.setPlainsKm(req.plainsKm());
        expense.setTotalAmount(req.totalAmount());
        expense.setBusFare(req.busFare());
        expense.setOthers(req.others());
        expense.setGrossTotal(req.grossTotal());
        expense.setRemarks(req.remarks());
        expense.setPetrolSlipPath(req.petrolSlipPath());
        expense.setPhotos(req.photos());
        expense.setStatus("PENDING");

        taExpenseRepository.save(expense);

        // Let HR know a claim is waiting, in-app and by SMS.
        String who = userRepository.findById(userId).map(User::getName).orElse("An employee");
        String detail = who + " submitted a claim for " + expense.getDate()
                + (expense.getGrossTotal() != null ? " — Rs." + expense.getGrossTotal() : "");
        for (User hr : approvers().values()) {
            notificationService.createAndPush(hr.getId(),
                    "New expense claim", detail, "CLAIM", "/ta-expenses");
            if (hr.getPhone() != null && !hr.getPhone().isBlank()) {
                smsService.send(hr.getPhone(), "Pixous HR: " + detail + ". Please review in the portal.");
            }
        }
        return toResponse(expense);
    }

    /**
     * Correct a claim. The owner may fix their own while it is still pending;
     * HR and admins may correct any of them, whatever the decision — a wrong
     * amount stays wrong otherwise, and they are the ones who answer for it.
     */
    @Transactional
    public TaExpenseResponse updateTaExpense(Long userId, Long id, TaExpenseRequest req) {
        TaExpense expense = taExpenseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("TA Expense"));
        boolean isApprover = com.pixous.hrportal.security.SecurityUtils.hasAuthority("CLAIM_APPROVE")
                || com.pixous.hrportal.security.SecurityUtils.hasAuthority("USER_MANAGE")
                || com.pixous.hrportal.security.SecurityUtils.hasAuthority("DASHBOARD_EXEC");

        if (!isApprover) {
            if (!expense.getUserId().equals(userId)) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, "You can only edit your own claim");
            }
            if (!"PENDING".equalsIgnoreCase(expense.getStatus())) {
                throw ApiException.business("This claim has already been reviewed and can no longer be edited");
            }
        }

        expense.setDate(req.date());
        expense.setLocation(req.location());
        expense.setCategory(req.category());
        expense.setStartingKm(req.startingKm());
        expense.setEndingKm(req.endingKm());
        expense.setTotalKm(req.totalKm());
        expense.setHillsKm(req.hillsKm());
        expense.setPlainsKm(req.plainsKm());
        expense.setTotalAmount(req.totalAmount());
        expense.setBusFare(req.busFare());
        expense.setOthers(req.others());
        expense.setGrossTotal(req.grossTotal());
        expense.setRemarks(req.remarks());
        expense.setPetrolSlipPath(req.petrolSlipPath());
        expense.setPhotos(req.photos());
        taExpenseRepository.save(expense);

        // The approvers are looking at this claim, so tell them it changed.
        String who = userRepository.findById(userId).map(User::getName).orElse("An employee");
        String amount = expense.getGrossTotal() != null ? " — Rs." + expense.getGrossTotal() : "";
        if (expense.getUserId().equals(userId)) {
            // The owner corrected it, so the people waiting to decide should know.
            String detail = who + " updated their claim for " + expense.getDate() + amount;
            for (User approver : approvers().values()) {
                notificationService.createAndPush(approver.getId(),
                        "Claim updated", detail, "CLAIM", "/ta-expenses");
            }
        } else {
            // Someone else corrected it — tell whoever raised it what changed.
            String detail = who + " corrected your claim for " + expense.getDate() + amount;
            notificationService.createAndPush(expense.getUserId(),
                    "Claim updated", detail, "CLAIM", "/ta-expenses");
            sms(expense.getUserId(), detail);
        }
        return toResponse(expense);
    }

    @Transactional(readOnly = true)
    public List<TaExpenseResponse> getMyTaExpenses(Long userId) {
        return taExpenseRepository.findByUserIdOrderByDateDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TaExpenseResponse> getAllTaExpenses() {
        return taExpenseRepository.findAll()
                .stream().map(this::toResponse).toList();
    }

    /**
     * Claims raised by the caller's own team (same designation title) — for a
     * Team Leader to view. Never exposes other teams.
     */
    @Transactional(readOnly = true)
    public List<TaExpenseResponse> getMyTeamTaExpenses(Long userId) {
        User me = userRepository.findById(userId).orElse(null);
        if (me == null) return List.of();
        String title = me.getDesignationTitle() == null ? "" : me.getDesignationTitle().trim();
        if (title.isEmpty()) return getMyTaExpenses(userId);

        java.util.Set<Long> teamIds = userRepository
                .findTeammatesByTitleOrDesignation(title, me.getDesignationId())
                .stream().map(User::getId).collect(java.util.stream.Collectors.toSet());

        return taExpenseRepository.findAll().stream()
                .filter(e -> teamIds.contains(e.getUserId()))
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .map(this::toResponse).toList();
    }

    @Transactional
    public TaExpenseResponse updateStatus(Long id, String status, String comment, Long deciderId) {
        TaExpense expense = taExpenseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("TA Expense"));

        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals("APPROVED") && !normalized.equals("REJECTED") && !normalized.equals("PENDING")) {
            throw ApiException.business("Status must be APPROVED, REJECTED or PENDING");
        }
        if (normalized.equals("REJECTED") && (comment == null || comment.isBlank())) {
            throw ApiException.business("A reason is required to reject a claim");
        }

        expense.setStatus(normalized);
        expense.setDecisionComment(comment);
        expense.setDecidedBy(deciderId);
        expense.setDecidedAt(java.time.LocalDateTime.now());
        taExpenseRepository.save(expense);

        // Tell the employee the outcome, with the reason when rejected.
        if (!normalized.equals("PENDING")) {
            String verb = normalized.toLowerCase();
            String detail = "Your claim for " + expense.getDate() + " was " + verb
                    + (comment != null && !comment.isBlank() ? ": " + comment : ".");
            notificationService.createAndPush(expense.getUserId(),
                    "Claim " + verb, detail, "CLAIM", "/ta-expenses");
            userRepository.findById(expense.getUserId())
                    .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                    .ifPresent(u -> smsService.send(u.getPhone(), "Pixous HR: " + detail));
        }
        return toResponse(expense);
    }

    /**
     * Withdraw a claim the person raised themselves.
     *
     * <p>Separate from delete, which removes the row: a claim that was raised
     * and then withdrawn is a thing that happened, and an approver who saw it
     * in their queue should find out what became of it rather than find it
     * gone. So the row stays and its status becomes CANCELLED.
     *
     * <p>Only the person who raised it, and only while it is still pending --
     * the same two rules editing already carries. Once HR has decided, the
     * decision is theirs to change, not the claimant's to erase.
     */
    @Transactional
    public void cancelTaExpense(Long currentUserId, Long id) {
        TaExpense expense = taExpenseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("TA Expense"));
        if (!expense.getUserId().equals(currentUserId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "You can only cancel your own claim");
        }
        if ("CANCELLED".equalsIgnoreCase(expense.getStatus())) {
            throw ApiException.business("This claim is already cancelled.");
        }
        if (!"PENDING".equalsIgnoreCase(expense.getStatus())) {
            throw ApiException.business(
                    "This claim has already been reviewed and can no longer be cancelled.");
        }
        expense.setStatus("CANCELLED");
        taExpenseRepository.save(expense);
    }

    @Transactional
    public void deleteTaExpense(Long currentUserId, Long id, boolean isAdmin) {
        TaExpense expense = taExpenseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("TA Expense"));
        if (!isAdmin && !expense.getUserId().equals(currentUserId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "You are not allowed to delete this claim");
        }
        taExpenseRepository.delete(expense);
    }

    /** Text a user if we have a usable mobile number for them. */
    private void sms(Long userId, String message) {
        if (userId == null) return;
        userRepository.findById(userId)
                .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                .ifPresent(u -> smsService.send(u.getPhone(), "Pixous HR: " + message));
    }

    /** Everyone who can act on a claim: HR (CLAIM_APPROVE) and admins (USER_MANAGE). */
    private java.util.Map<Long, User> approvers() {
        java.util.Map<Long, User> byId = new java.util.LinkedHashMap<>();
        userRepository.findByPermission("CLAIM_APPROVE").forEach(u -> byId.put(u.getId(), u));
        userRepository.findByPermission("USER_MANAGE").forEach(u -> byId.put(u.getId(), u));
        return byId;
    }

    private TaExpenseResponse toResponse(TaExpense e) {
        User owner = e.getUser() != null ? e.getUser()
                : userRepository.findById(e.getUserId()).orElse(null);
        String decidedByName = e.getDecidedBy() == null ? null
                : userRepository.findById(e.getDecidedBy()).map(User::getName).orElse(null);

        return new TaExpenseResponse(
                e.getId(), e.getUserId(),
                owner != null ? owner.getName() : "Unknown",
                owner != null ? owner.getEmployeeCode() : null,
                owner != null ? owner.getDesignationTitle() : null,
                e.getDate(), e.getLocation(), e.getCategory(),
                e.getStartingKm(), e.getEndingKm(), e.getTotalKm(),
                e.getHillsKm(), e.getPlainsKm(), e.getTotalAmount(),
                e.getBusFare(), e.getOthers(), e.getGrossTotal(),
                e.getRemarks(), e.getStatus(), e.getPetrolSlipPath(), e.getPhotos(),
                e.getDecisionComment(), decidedByName, e.getDecidedAt()
        );
    }
}
