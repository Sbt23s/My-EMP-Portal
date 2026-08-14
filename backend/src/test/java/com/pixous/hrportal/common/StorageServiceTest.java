package com.pixous.hrportal.common;

import com.pixous.hrportal.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the upload extension allowlist and empty-file handling.
 *
 * <p>The property that matters is the one the security comment states: a file
 * whose extension is not on the allowlist (HTML, SVG, JSP, ...) must never be
 * stored under an extension a browser would render on the API's own origin.
 */
class StorageServiceTest {

    private final FileEntityRepository repository = mock(FileEntityRepository.class);
    private StorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("secret-secret-secret-secret-secret-secret-secret-secret", 3600, 3600, "hr-portal"),
                new AppProperties.Cors(List.of()),
                new AppProperties.Storage("local", tempDir.toString()),
                new AppProperties.Attendance(200, 0, 8, "09:00", "18:00"),
                new AppProperties.Security(5, 15),
                new AppProperties.Twilio(false, "", "", "", "+91"),
                new AppProperties.Fast2sms(false, "", "q", "")
        );
        storageService = new StorageService(props, repository);
    }

    private String storedPathFor(MockMultipartFile file) {
        storageService.store(file, "documents");
        ArgumentCaptor<FileEntity> captor = ArgumentCaptor.forClass(FileEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue().getId();
    }

    @Test
    void keepsAllowlistedExtension() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", new byte[]{1, 2, 3});
        String path = storedPathFor(pdf);
        assertThat(path).endsWith(".pdf");
    }

    @Test
    void htmlUploadIsStoredAsBin() {
        MockMultipartFile html = new MockMultipartFile(
                "file", "evil.html", "text/html", "<script>alert(1)</script>".getBytes());
        String path = storedPathFor(html);
        assertThat(path).endsWith(".bin");
    }

    @Test
    void svgUploadIsStoredAsBin() {
        // SVG can carry script; it must never be served inline from our origin.
        MockMultipartFile svg = new MockMultipartFile(
                "file", "icon.svg", "image/svg+xml", "<svg/>".getBytes());
        String path = storedPathFor(svg);
        assertThat(path).endsWith(".bin");
    }

    @Test
    void doubleExtensionTakesLastPart() {
        MockMultipartFile sneaky = new MockMultipartFile(
                "file", "photo.jpg.php", "application/octet-stream", new byte[]{1});
        String path = storedPathFor(sneaky);
        assertThat(path).endsWith(".bin");
    }

    @Test
    void uppercaseExtensionIsNormalized() {
        MockMultipartFile upper = new MockMultipartFile(
                "file", "SCAN.PDF", "application/pdf", new byte[]{1});
        String path = storedPathFor(upper);
        assertThat(path).endsWith(".pdf");
    }

    @Test
    void noExtensionBecomesBin() {
        MockMultipartFile noExt = new MockMultipartFile(
                "file", "README", "text/plain", "hi".getBytes());
        String path = storedPathFor(noExt);
        assertThat(path).endsWith(".bin");
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> storageService.store(empty, "documents"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("empty");
        verify(repository, never()).save(any());
    }

    @Test
    void uploadedFilenameIsNeverUsedInPath() {
        // Path-traversal style names must not affect where the file is stored.
        MockMultipartFile traversal = new MockMultipartFile(
                "file", "../../etc/passwd.pdf", "application/pdf", new byte[]{1});
        String path = storedPathFor(traversal);
        assertThat(path).doesNotContain("..").doesNotContain("passwd");
    }
}
