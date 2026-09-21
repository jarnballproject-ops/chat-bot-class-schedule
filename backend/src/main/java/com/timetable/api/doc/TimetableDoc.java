package com.timetable.api.doc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** ไฟล์ตารางหนึ่งไฟล์ที่เจ้าหน้าที่อัปโหลดไว้ให้ OCR อ่านทีหลัง */
@Entity
@Table(name = "timetable_doc")
public class TimetableDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String semester;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(nullable = false)
    private String status;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    public static TimetableDoc of(Long teacherId, String kind, String semester, String filename,
                                  String contentType, long sizeBytes, String sha256) {
        TimetableDoc doc = new TimetableDoc();
        doc.teacherId = teacherId;
        doc.kind = kind;
        doc.semester = semester;
        doc.filename = filename;
        doc.contentType = contentType;
        doc.sizeBytes = sizeBytes;
        doc.sha256 = sha256;
        doc.status = "uploaded";
        return doc;
    }

    @PrePersist
    void onInsert() {
        uploadedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public String getKind() {
        return kind;
    }

    public String getSemester() {
        return semester;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }
}
