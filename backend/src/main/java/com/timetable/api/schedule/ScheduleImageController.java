package com.timetable.api.schedule;

import com.timetable.api.auth.RequireRole;
import com.timetable.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * รูปตารางต้นฉบับที่ใช้สกัดข้อมูล ให้หน้าเว็บเอาไว้เทียบกับคาบที่ระบบอ่านมาได้
 *
 * <p><b>ความปลอดภัย:</b> ไม่รับ path จาก client มาเปิดไฟล์ตรง ๆ รับมาแล้วต้องมีอยู่ใน
 * session.source_image ก่อนเสมอ (allowlist จากข้อมูลที่เราเขียนเอง) แล้วยังเช็คซ้ำว่า
 * path ที่ resolve แล้วยังอยู่ใต้โฟลเดอร์ราก กัน path traversal สองชั้น
 */
@RestController
@RequestMapping("/api/v1")
public class ScheduleImageController {

    private static final String FOLDER_PREFIX = "ตารางสอน";

    private final JdbcClient jdbc;
    private final Path root;

    public ScheduleImageController(JdbcClient jdbc, @Value("${app.schedule-images.dir}") String dir) {
        this.jdbc = jdbc;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    /** รูปทั้งหมดของอาจารย์คนหนึ่ง เรียงตามช่วงสัปดาห์ ใบละรูป */
    @GetMapping("/teachers/{id}/images")
    @RequireRole({"student", "staff", "teacher"})
    public List<ScheduleImage> images(@PathVariable Long id) {
        return jdbc.sql("""
                        select s.source_image as path, s.week_from as week_from, s.week_to as week_to,
                               max(s.source_note) as note, count(*) as sessions
                        from session s
                                 join session_teacher st on st.session_id = s.id
                        where st.teacher_id = :id and s.source_image is not null
                        group by s.source_image, s.week_from, s.week_to
                        order by s.week_from nulls last, s.source_image
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new ScheduleImage(
                        rs.getString("path"),
                        (Integer) rs.getObject("week_from"),
                        (Integer) rs.getObject("week_to"),
                        rs.getString("note"),
                        rs.getInt("sessions")))
                .list();
    }

    @GetMapping("/schedule-images")
    @RequireRole({"student", "staff", "teacher"})
    public ResponseEntity<Resource> file(@RequestParam String path) {
        Boolean known = jdbc.sql("select exists (select 1 from session where source_image = :path)")
                .param("path", path)
                .query(Boolean.class)
                .single();
        if (!Boolean.TRUE.equals(known)) {
            throw ApiException.notFound("image-not-found", "ไม่พบรูปตารางนี้ในระบบ");
        }

        Path resolved = resolve(path);
        if (resolved == null) {
            throw ApiException.notFound("image-not-found", "ไม่พบไฟล์รูปตาราง: " + path);
        }

        return ResponseEntity.ok()
                .contentType(mediaType(resolved))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(resolved.getFileName().toString()).toString())
                .body(new FileSystemResource(resolved));
    }

    /**
     * ตัวสกัดเขียน sourceFile เป็น "IT/xxx.png" แต่โฟลเดอร์บนดิสก์ชื่อ "ตารางสอนIT"
     * จึงลองทั้งชื่อตรงและชื่อที่เติมคำนำหน้า รับได้ทั้งไฟล์ชุดเก่าและชุดใหม่โดยไม่ต้องแก้ข้อมูล
     * คืน null เมื่อหาไม่เจอหรือ path หลุดออกนอกโฟลเดอร์ราก
     */
    private Path resolve(String path) {
        for (String candidate : new String[]{path, FOLDER_PREFIX + path}) {
            Path file = root.resolve(candidate).normalize();
            if (file.startsWith(root) && Files.isRegularFile(file)) {
                return file;
            }
        }
        return null;
    }

    private static MediaType mediaType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_PNG;
    }

    /**
     * sessions = จำนวนคาบที่สกัดได้จากรูปใบนี้ ใช้บอกผู้ใช้ว่ารูปนี้ให้ข้อมูลมาเท่าไร
     * note = ข้อสังเกตของตัวสกัด เช่น หัวตารางถูกตัดขอบจนอ่านได้ไม่ครบ คนตรวจทานต้องเห็นคู่กับรูป
     */
    public record ScheduleImage(String path, Integer weekFrom, Integer weekTo, String note, int sessions) {
    }
}
