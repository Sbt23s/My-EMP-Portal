package com.pixous.hrportal.modules.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One Excel upload that created employee accounts.
 *
 * <p>Kept so an import can be undone. The record outlives the accounts it made:
 * reverting stamps {@code revertedAt} rather than deleting the row, so the
 * history still shows that a sheet was uploaded and then taken back out.
 */
@Getter
@Setter
@Entity
@Table(name = "employee_imports")
public class EmployeeImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The name of the uploaded sheet, as the browser reported it. */
    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "imported_by")
    private Long importedBy;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt = LocalDateTime.now();

    /** Rows in the sheet, whether they worked or not. */
    @Column(name = "total_rows", nullable = false)
    private int totalRows = 0;

    @Column(name = "created_count", nullable = false)
    private int createdCount = 0;

    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;

    @Column(name = "reverted_at")
    private LocalDateTime revertedAt;

    @Column(name = "reverted_by")
    private Long revertedBy;
}
