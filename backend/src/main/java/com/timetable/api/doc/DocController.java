package com.timetable.api.doc;

import com.timetable.api.auth.RequireRole;
import com.timetable.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@RestController
@RequestMapping("/api/v1/docs")
public class DocController {

    /** ชนิดไฟล์ที่ OCR อ่านได้จริง กันไฟล์อื่นตั้งแต่ขอบเขตความเชื่อถือ */
    private static final List<String> ALLOWED = List.of("application/pdf", "image/jpeg", "image/png", "image/webp");

    private final TimetableDocRepository repository;
    private final Path uploadDir;

    public DocController(TimetableDocRepository repository, @Value("${app.uploads.dir}") String uploadDir)
            throws IOException {
        this.repository = repository;
        this.uploadDir = Path.of(uploadDir);
        Files.createDirectories(this.uploadDir);
    }

    @GetMapping
    @RequireRole({"staff", "teacher"})
    public List<TimetableDoc> list(@RequestParam(required = false) Long teacherId) {
        return teacherId == null
                ? repository.findAllByOrderByUploadedAtDesc()
                : repository.findByTeacherIdOrderByUploadedAtDesc(teacherId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole("staff")
    public UploadResult upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) Long teacherId,
                               @RequestParam(defaultValue = "teacher") String kind,
                               @RequestParam String semester) throws IOException {

        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "empty-file", "ไฟล์ที่อัปโหลดว่างเปล่า");
        }
        if (!ALLOWED.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "unsupported-file",
                    "รับเฉพาะ PDF หรือรูปภาพ (jpg, png, webp)");
        }

        byte[] bytes = file.getBytes();
        String sha256 = sha256(bytes);

        // ไฟล์เดิมที่เคยอัปแล้วคืน doc เดิม ไม่สร้างงานซ้ำและไม่เขียนไฟล์ทับ
        TimetableDoc existing = repository.findBySha256(sha256).orElse(null);
        if (existing != null) {
            return new UploadResult(existing.getId(), existing.getStatus(), true);
        }

        Files.write(uploadDir.resolve(sha256), bytes);
        TimetableDoc saved = repository.save(TimetableDoc.of(teacherId, kind, semester,
                file.getOriginalFilename(), file.getContentType(), bytes.length, sha256));
        return new UploadResult(saved.getId(), saved.getStatus(), false);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record UploadResult(Long docId, String state, boolean duplicate) {
    }
}
