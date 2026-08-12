package com.pixous.hrportal.common;

import com.pixous.hrportal.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Database storage for documents, photos, payslips, QR images, etc., with fallback to local-disk storage.
 * The {@code app.storage.type} switch leaves room for an S3/MinIO impl in prod
 * without touching callers.
 */
@Slf4j
@Service
public class StorageService {

    private final Path root;
    private final FileEntityRepository fileEntityRepository;

    public StorageService(AppProperties props, FileEntityRepository fileEntityRepository) {
        this.root = Paths.get(props.storage().localPath()).toAbsolutePath().normalize();
        this.fileEntityRepository = fileEntityRepository;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage root " + root, e);
        }
    }

    /**
     * Extensions that are safe for a browser to open on our own origin.
     *
     * Everything else keeps its bytes but is stored under ".bin", so
     * FileController can never be talked into serving it as text/html or
     * image/svg+xml. Without this, uploading "x.html" produced a file the server
     * handed back as HTML from a public URL — a script running on the API's own
     * origin, from a link that looks entirely legitimate.
     *
     * SVG is deliberately not here: it is an image everywhere except that it can
     * carry script.
     */
    private static final java.util.Set<String> INLINE_SAFE_EXTENSIONS = java.util.Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico",
            "pdf", "txt", "csv",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "mp3", "wav", "m4a", "ogg", "mp4", "webm"
    );

    /** Lower-cased, stripped of anything that is not a letter or digit, allowlisted. */
    private static String safeExtension(String raw) {
        String ext = raw == null ? "" : raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ext.isEmpty() || ext.length() > 8) return "bin";
        return INLINE_SAFE_EXTENSIONS.contains(ext) ? ext : "bin";
    }

    /** Stores a multipart upload under {@code <folder>/<yyyy-MM>/<uuid>.<ext>} in DB and returns the relative path. */
    public String store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw ApiException.business("File is empty");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1) : "bin";
        ext = safeExtension(ext);
        String relative = folder + "/" + LocalDate.now().toString().substring(0, 7)
                + "/" + UUID.randomUUID() + "." + ext;
        try {
            FileEntity entity = FileEntity.builder()
                .id(relative)
                .fileName(original)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .data(file.getBytes())
                .build();
            fileEntityRepository.save(entity);
            return relative;
        } catch (IOException e) {
            log.error("Failed to store file", e);
            throw new ApiException(ErrorCode.INTERNAL, "Could not store file");
        }
    }

    /** Writes raw bytes (e.g. a generated PDF / QR png) to DB and returns the relative path. */
    public String storeBytes(byte[] content, String folder, String filename) {
        String relative = folder + "/" + filename;
        FileEntity entity = FileEntity.builder()
            .id(relative)
            .fileName(filename)
            .contentType("application/octet-stream")
            .sizeBytes((long) content.length)
            .data(content)
            .build();
        fileEntityRepository.save(entity);
        return relative;
    }

    public byte[] read(String relativePath) {
        // Try reading from database first
        Optional<FileEntity> fileOpt = fileEntityRepository.findById(relativePath);
        if (fileOpt.isPresent()) {
            return fileOpt.get().getData();
        }

        // Fallback to local filesystem for older files
        try {
            Path target = root.resolve(relativePath).normalize();
            if (Files.exists(target)) {
                return Files.readAllBytes(target);
            }
        } catch (IOException e) {
            log.warn("Failed to read file from local fallback: {}", relativePath, e);
        }

        throw ApiException.notFound("File");
    }

    public Path resolve(String relativePath) {
        return root.resolve(relativePath).normalize();
    }
}
