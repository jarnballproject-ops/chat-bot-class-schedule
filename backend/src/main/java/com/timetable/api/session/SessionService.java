package com.timetable.api.session;

import com.timetable.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SessionService {

    private final SessionRepository repository;
    private final JdbcClient jdbc;

    public SessionService(SessionRepository repository, JdbcClient jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /** ผลการแก้คาบ ตามสัญญาใน docs/แผนงาน.md ข้อ 6.3 */
    public record PatchResult(Session session, int correctionsLogged) {
    }

    @Transactional
    public PatchResult patch(Long id, SessionPatch patch, String correctedBy) {
        Session session = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("session-not-found", "ไม่พบคาบ id=" + id));

        // เก็บค่าก่อนแก้ไว้ก่อน เพราะ field_correction ต้องการคู่ (ค่าที่ OCR อ่านได้, ค่าที่คนยืนยัน)
        List<Correction> corrections = new ArrayList<>();

        if (patch.dayOfWeek() != null) {
            if (patch.dayOfWeek() < 1 || patch.dayOfWeek() > 7) {
                throw invalid("วันต้องอยู่ระหว่าง 1 (จันทร์) ถึง 7 (อาทิตย์)");
            }
            corrections.add(Correction.of("day_of_week", session.getDayOfWeek(), patch.dayOfWeek()));
            session.setDayOfWeek(patch.dayOfWeek());
        }
        if (patch.startTime() != null) {
            corrections.add(Correction.of("start_time", session.getStartTime(), patch.startTime()));
            session.setStartTime(patch.startTime());
        }
        if (patch.endTime() != null) {
            corrections.add(Correction.of("end_time", session.getEndTime(), patch.endTime()));
            session.setEndTime(patch.endTime());
        }
        LocalTime start = session.getStartTime();
        LocalTime end = session.getEndTime();
        if (end == null || start == null || !end.isAfter(start)) {
            throw invalid("เวลาจบต้องหลังเวลาเริ่ม");
        }
        if (patch.kind() != null) {
            if (!patch.kind().equals("ทฤษฎี") && !patch.kind().equals("ปฏิบัติ")) {
                throw invalid("ประเภทคาบต้องเป็น ทฤษฎี หรือ ปฏิบัติ");
            }
            corrections.add(Correction.of("kind", session.getKind(), patch.kind()));
            session.setKind(patch.kind());
        }

        // รหัสที่ยังไม่มีในตารางอ้างอิงให้สร้างขึ้นก่อน ไม่งั้น FK ของ session พัง
        // เจ้าหน้าที่แก้ชื่อห้อง/วิชาจากตารางจริงที่ระบบยังไม่รู้จักได้เป็นเรื่องปกติ
        if (patch.subjectCode() != null) {
            String name = patch.subjectName() != null ? patch.subjectName() : patch.subjectCode();
            jdbc.sql("insert into subject (code, name) values (?, ?) on conflict (code) do update set name = excluded.name")
                    .params(patch.subjectCode(), name)
                    .update();
            corrections.add(Correction.of("subject_code", session.getSubjectCode(), patch.subjectCode()));
            session.setSubjectCode(patch.subjectCode());
        } else if (patch.subjectName() != null && session.getSubjectCode() != null) {
            jdbc.sql("update subject set name = ? where code = ?")
                    .params(patch.subjectName(), session.getSubjectCode())
                    .update();
        }
        if (patch.roomCode() != null) {
            jdbc.sql("insert into room (code, name) values (?, ?) on conflict (code) do nothing")
                    .params(patch.roomCode(), patch.roomCode())
                    .update();
            corrections.add(Correction.of("room_code", session.getRoomCode(), patch.roomCode()));
            session.setRoomCode(patch.roomCode());
        }
        if (patch.groupCode() != null) {
            jdbc.sql("insert into class_group (code, name) values (?, ?) on conflict (code) do nothing")
                    .params(patch.groupCode(), patch.groupCode())
                    .update();
            corrections.add(Correction.of("group_code", session.getGroupCode(), patch.groupCode()));
            session.setGroupCode(patch.groupCode());
        }

        // ponytail: ยังไม่กันคาบชนกันเอง หน้าตารางแสดงคาบทับเป็นแถวย่อยอยู่แล้ว
        // เพิ่มตรวจชนเมื่อเริ่มใช้กับตารางหลายกลุ่มพร้อมกัน (FR-16)
        Session saved = repository.save(session);

        // บันทึกทุกฟิลด์ที่คนยืนยัน ไม่ใช่เฉพาะฟิลด์ที่ค่าเปลี่ยน
        // เพราะสูตรความแม่นในข้อ 10 คือ COUNT(ocr_value = human_value) / COUNT(*)
        // ถ้าเก็บเฉพาะที่แก้ ตัวหารจะมีแต่เคสที่ OCR ผิด แล้วความแม่นจะออกมาเป็น 0% เสมอ
        for (Correction c : corrections) {
            jdbc.sql("""
                            insert into field_correction (session_id, field_name, old_value, new_value, corrected_by)
                            values (?, ?, ?, ?, ?)
                            """)
                    .params(id, c.field(), c.oldValue(), c.newValue(), correctedBy)
                    .update();
        }

        return new PatchResult(saved, corrections.size());
    }

    /**
     * ลบคาบที่ระบบอ่านมาเกิน (คาบซ้ำ หรือช่องที่ไม่ใช่คาบเรียนจริง) ใช้ได้เฉพาะเจ้าหน้าที่กับอาจารย์
     *
     * <p>session_teacher และ field_correction เป็น on delete cascade จึงหายตามไปด้วย
     * ซึ่งแปลว่าช่องที่เคยตรวจทานของคาบนี้หลุดจากฐานความแม่นไปด้วย ยอมรับได้
     * เพราะคาบที่ไม่มีอยู่จริงไม่ควรถูกนับเป็น ground truth ตั้งแต่แรก
     */
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound("session-not-found", "ไม่พบคาบ id=" + id);
        }
        repository.deleteById(id);
    }

    private record Correction(String field, String oldValue, String newValue) {
        static Correction of(String field, Object oldValue, Object newValue) {
            return new Correction(field, str(oldValue), str(newValue));
        }

        private static String str(Object v) {
            return v == null ? null : Objects.toString(v);
        }
    }

    private ApiException invalid(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-session", detail);
    }
}
