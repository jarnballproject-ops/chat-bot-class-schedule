package com.timetable.api.chat;

import com.timetable.api.chat.ScheduleContext.Payload;
import com.timetable.api.chat.ScheduleContext.Slot;
import com.timetable.api.chat.ScheduleContext.Subject;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * ประกอบตารางของอาจารย์หนึ่งคนเป็น JSON รูปเดียวกับ dataset/schedule.json ให้โมเดลอ่าน
 * อ่านจาก Ready Data (session ที่ publish แล้ว) เท่านั้น โมเดลจึงไม่เคยเห็นข้อมูลที่ยังไม่ตรวจทาน (ADR-01)
 */
@Service
public class ScheduleContextBuilder {

    private static final String[] DAY_NAMES =
            {"จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์", "อาทิตย์"};

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public ScheduleContextBuilder(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public ScheduleContext forTeacher(long teacherId, String teacherLabel) {
        List<Raw> rows = jdbc.sql("""
                        select s.day_of_week, s.start_time, s.end_time, s.kind, s.semester,
                               s.subject_code, sub.name as subject_name, sub.theory, sub.practice, sub.credits,
                               s.room_code, s.group_code, g.headcount, g.parent_code
                        from session s
                                 join session_teacher st on st.session_id = s.id
                                 left join subject sub on sub.code = s.subject_code
                                 left join class_group g on g.code = s.group_code
                        where st.teacher_id = :teacherId
                          and s.status = 'published'
                        order by s.day_of_week, s.start_time
                        """)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> new Raw(
                        rs.getInt("day_of_week"),
                        rs.getTime("start_time").toLocalTime().getHour(),
                        rs.getTime("end_time").toLocalTime().getHour(),
                        rs.getString("kind"),
                        rs.getString("semester"),
                        rs.getString("subject_code"),
                        rs.getString("subject_name"),
                        (Integer) rs.getObject("theory"),
                        (Integer) rs.getObject("practice"),
                        (Integer) rs.getObject("credits"),
                        rs.getString("room_code"),
                        rs.getString("group_code"),
                        (Integer) rs.getObject("headcount"),
                        rs.getString("parent_code")))
                .list();

        Map<String, Subject> subjects = new LinkedHashMap<>();
        Map<String, Integer> groups = new TreeMap<>();
        Map<String, List<String>> combined = new LinkedHashMap<>();
        List<Slot> slots = new ArrayList<>();
        Set<Integer> hours = new LinkedHashSet<>();
        String semester = "";

        for (Raw row : rows) {
            semester = row.semester();
            if (row.subjectCode() != null) {
                subjects.putIfAbsent(row.subjectCode(),
                        new Subject(row.subjectName(), row.theory(), row.practice(), row.credits()));
            }
            if (row.groupCode() != null) {
                groups.putIfAbsent(row.groupCode(), row.headcount());
                // กลุ่มรวม (สท.4/1-2) ต้องบอกว่าประกอบจากกลุ่มย่อยไหนบ้าง ไม่งั้นโมเดลนับจำนวนคนผิด
                if (row.parentCode() != null) {
                    combined.computeIfAbsent(row.parentCode(), k -> new ArrayList<>());
                    if (!combined.get(row.parentCode()).contains(row.groupCode())) {
                        combined.get(row.parentCode()).add(row.groupCode());
                    }
                }
            }
            slots.add(new Slot(DAY_NAMES[row.day() - 1], row.start(), row.end(),
                    row.subjectCode(), row.kind(), row.roomCode(), row.groupCode()));
            hours.add(row.start());
            hours.add(row.end());
        }

        Payload payload = new Payload(teacherLabel, semester, subjects, groups, combined, slots);
        return new ScheduleContext(objectMapper.writeValueAsString(payload),
                hours, subjects.keySet(), groups.keySet(), slots.isEmpty());
    }

    private record Raw(int day, int start, int end, String kind, String semester,
                       String subjectCode, String subjectName, Integer theory, Integer practice,
                       Integer credits, String roomCode, String groupCode, Integer headcount,
                       String parentCode) {
    }
}
