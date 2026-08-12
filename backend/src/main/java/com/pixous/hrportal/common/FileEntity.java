package com.pixous.hrportal.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileEntity {
    
    @Id
    @Column(name = "id", nullable = false, length = 255)
    private String id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Lob
    @Column(name = "data", nullable = false, columnDefinition="LONGBLOB")
    private byte[] data;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "company_id", length = 36)
    private String companyId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
