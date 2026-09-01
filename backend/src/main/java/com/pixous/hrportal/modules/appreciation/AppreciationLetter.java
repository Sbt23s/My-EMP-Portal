package com.pixous.hrportal.modules.appreciation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An appreciation letter: HR, an administrator or the CTO writes one about an
 * employee, and the employee receives it.
 *
 * <p>The mirror image of a discipline record, and its own table for the same
 * reason that one is: they are read by different people for different reasons,
 * and folding them together would leave every query having to say which kind
 * it meant.
 */
@Getter
@Setter
@Entity
@Table(name = "appreciation_letters")
public class AppreciationLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AL-2026-00001. Generated, unique, and what the letter is filed under. */
    @Column(name = "reference_code", nullable = false, unique = true, length = 30)
    private String referenceCode;

    /** The person being appreciated. */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** Whoever wrote it -- HR, an administrator or the CTO. */
    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "letter_date", nullable = false)
    private LocalDate letterDate;

    @Column(nullable = false, length = 120)
    private String achievement;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Which of the letter designs this one uses. */
    @Column(nullable = false, length = 40)
    private String template = "CLASSIC";

    /** DRAFT | SENT */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    /**
     * When the employee first opened it, and when they first downloaded it.
     *
     * <p>Recorded so whoever wrote it can see the letter actually landed --
     * an appreciation nobody read is worth knowing about.
     */
    @Column(name = "viewed_at")
    private LocalDateTime viewedAt;

    @Column(name = "downloaded_at")
    private LocalDateTime downloadedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
