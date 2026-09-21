package com.timetable.api.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * นำเข้าตารางสอนจาก schedules_*.json เข้า Ready Data (subject/room/class_group/teacher/session)
 * แทนสาย OCR เดิมที่ถูกตัดออก
 *
 * <p>รันซ้ำได้: ล้างคาบของครูที่อยู่ในไฟล์ก่อนใส่ใหม่ ไม่แตะคาบของครูคนอื่น
 * ทำให้เพิ่มแผนกทีละไฟล์ได้โดยไม่ต้องล้างทั้งฐาน
 */
@Service
public class ScheduleImportService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleImportService.class);

    private static final Map<String, Integer> DAYS = Map.of(
            "จันทร์", 1, "อังคาร", 2, "พุธ", 3, "พฤหัสบดี", 4,
            "พฤหัส", 4, "ศุกร์", 5, "เสาร์", 6, "อาทิตย์", 7);

    /** เรียงยาวไปสั้น เพราะ "นางสาว" ต้องถูกตัดก่อน "นาง" */
    private static final List<String> PREFIXES = List.of(
            "ว่าที่ร้อยตรีหญิง", "ว่าที่ร้อยตรี", "ว่าที่ ร.ต.", "นางสาว", "นาง", "นาย",
            "ผศ.ดร.", "รศ.ดร.", "ผศ.", "รศ.", "ดร.");

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public ScheduleImportService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScheduleImportResult importFile(Path file) {
        List<ScheduleJson> docs;
        try {
            docs = objectMapper.readValue(Files.readString(file), new TypeReference<List<ScheduleJson>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("อ่านไฟล์ " + file + " ไม่ได้: " + e.getMessage(), e);
        }

        List<String> warnings = new ArrayList<>();
        List<ScheduleJson> usable = new ArrayList<>();
        for (ScheduleJson doc : docs) {
            if (doc.teacher() == null || doc.teacher().isBlank()) {
                // ไม่มีชื่อครู = ผูกคาบเข้าทะเบียนครูไม่ได้ และแชทค้นไม่เจอ ข้ามไปดีกว่าใส่แล้วหาไม่เจอ
                warnings.add("ไม่มีชื่อครู ข้ามทั้งใบ: " + doc.sourceFile());
                continue;
            }
            if (doc.resolvedWeekFrom() == null || doc.resolvedWeekTo() == null) {
                warnings.add("ไม่มีช่วงสัปดาห์ คาบอาจซ้อนกับใบอื่นของครูคนเดียวกัน: " + doc.sourceFile());
            }
            usable.add(doc);
        }

        upsertSubjects(usable, warnings);
        upsertRooms(usable);
        upsertGroups(usable, warnings);
        Map<String, Long> teacherIds = upsertTeachers(usable);
        upsertAliases(usable, teacherIds, warnings);
        int sessions = replaceSessions(usable, teacherIds, warnings);

        ScheduleImportResult result = new ScheduleImportResult(
                file.getFileName().toString(), docs.size(), usable.size(),
                teacherIds.size(), sessions, warnings);
        log.info("นำเข้าตาราง {}: {} ใบ ({} ใบใช้ได้), ครู {} คน, {} คาบ, เตือน {} ข้อ",
                result.file(), result.documents(), result.imported(),
                result.teachers(), result.sessions(), warnings.size());
        return result;
    }

    private void upsertSubjects(List<ScheduleJson> docs, List<String> warnings) {
        Map<String, ScheduleJson.Subject> byCode = new LinkedHashMap<>();
        for (ScheduleJson doc : docs) {
            if (doc.subjects() != null) {
                byCode.putAll(doc.subjects());
            }
        }
        // บาง slot อ้างรหัสวิชาที่ไม่มีในตารางรายวิชาด้านบนของใบนั้น ใส่ตัวแทนไว้ก่อนเพื่อไม่ให้ FK พัง
        for (ScheduleJson doc : docs) {
            for (ScheduleJson.Slot slot : slots(doc)) {
                if (slot.code() != null && !byCode.containsKey(slot.code())) {
                    warnings.add("รหัสวิชา " + slot.code() + " ไม่มีในรายวิชาของ " + doc.sourceFile()
                            + " ใส่ไว้โดยยังไม่มีชื่อวิชา");
                    byCode.put(slot.code(), new ScheduleJson.Subject(slot.code(), null, null, null));
                }
            }
        }
        byCode.forEach((code, subject) -> jdbc.sql("""
                        insert into subject (code, name, theory, practice, credits)
                        values (:code, :name, :theory, :practice, :credits)
                        on conflict (code) do update set name = excluded.name, theory = excluded.theory,
                            practice = excluded.practice, credits = excluded.credits
                        """)
                .param("code", code)
                .param("name", subject.name() == null ? code : subject.name())
                .param("theory", subject.theory())
                .param("practice", subject.practice())
                .param("credits", subject.credits())
                .update());
    }

    private void upsertRooms(List<ScheduleJson> docs) {
        Set<String> rooms = new LinkedHashSet<>();
        for (ScheduleJson doc : docs) {
            for (ScheduleJson.Slot slot : slots(doc)) {
                if (slot.room() != null && !slot.room().isBlank()) {
                    rooms.add(slot.room().trim());
                }
            }
        }
        for (String room : rooms) {
            jdbc.sql("insert into room (code, name) values (:code, :code) on conflict (code) do nothing")
                    .param("code", room)
                    .update();
        }
    }

    /**
     * กลุ่มรวมต้องเข้าก่อนกลุ่มย่อยเพราะ parent_code เป็น FK ชี้ตัวเอง
     * กลุ่มที่ slot อ้างแต่ไม่มีใน groups ก็ต้องใส่ ไม่งั้น session insert ไม่ผ่าน
     */
    private void upsertGroups(List<ScheduleJson> docs, List<String> warnings) {
        Map<String, Integer> headcounts = new LinkedHashMap<>();
        Map<String, String> parentOf = new HashMap<>();

        for (ScheduleJson doc : docs) {
            if (doc.groups() != null) {
                headcounts.putAll(doc.groups());
            }
            if (doc.combinedGroups() != null) {
                doc.combinedGroups().forEach((parent, children) -> {
                    headcounts.putIfAbsent(parent, null);
                    if (children != null) {
                        for (String child : children) {
                            headcounts.putIfAbsent(child, null);
                            parentOf.put(child, parent);
                        }
                    }
                });
            }
        }
        for (ScheduleJson doc : docs) {
            for (ScheduleJson.Slot slot : slots(doc)) {
                if (slot.group() != null && !slot.group().isBlank()
                        && !headcounts.containsKey(slot.group())) {
                    warnings.add("กลุ่ม " + slot.group() + " ไม่มีในรายชื่อกลุ่มของ " + doc.sourceFile()
                            + " ใส่ไว้โดยยังไม่มีจำนวนคน");
                    headcounts.put(slot.group(), null);
                }
            }
        }

        // รอบแรกไม่ใส่ parent เพื่อให้ทุกกลุ่มมีตัวตนก่อน รอบสองค่อยผูกสายแม่ลูก
        headcounts.forEach((code, headcount) -> jdbc.sql("""
                        insert into class_group (code, name, headcount) values (:code, :code, :headcount)
                        on conflict (code) do update set headcount = coalesce(excluded.headcount, class_group.headcount)
                        """)
                .param("code", code)
                .param("headcount", headcount)
                .update());
        parentOf.forEach((child, parent) -> jdbc.sql(
                        "update class_group set parent_code = :parent where code = :child")
                .param("parent", parent)
                .param("child", child)
                .update());
    }

    /**
     * คืน map ชื่อครูเต็ม -> teacher.id
     *
     * <p>ครูคนเดียวมีหลายใบและบางใบหัวตารางถูกตัดขอบจนฟิลด์หาย จึงเก็บค่าแรกที่ไม่ว่างของแต่ละฟิลด์
     * แล้ว coalesce ตอน upsert ไม่ให้ใบที่ข้อมูลขาดลบค่าที่ใบก่อนหน้าให้มาแล้ว
     */
    private Map<String, Long> upsertTeachers(List<ScheduleJson> docs) {
        Map<String, Profile> profiles = new LinkedHashMap<>();
        for (ScheduleJson doc : docs) {
            profiles.merge(doc.teacher().trim(), Profile.of(doc), Profile::fillFrom);
        }

        Map<String, Long> ids = new LinkedHashMap<>();
        profiles.forEach((fullName, profile) -> {
            String[] parts = splitName(fullName);
            Long id = jdbc.sql("""
                            insert into teacher (code, prefix, first_name, last_name, department,
                                college, education, special_duty)
                            values (:code, :prefix, :first, :last, :department,
                                :college, :education, :specialDuty)
                            on conflict (code) do update set prefix = excluded.prefix,
                                first_name = excluded.first_name, last_name = excluded.last_name,
                                department = coalesce(excluded.department, teacher.department),
                                college = coalesce(excluded.college, teacher.college),
                                education = coalesce(excluded.education, teacher.education),
                                special_duty = coalesce(excluded.special_duty, teacher.special_duty),
                                updated_at = now()
                            returning id
                            """)
                    .param("code", teacherCode(fullName))
                    .param("prefix", parts[0])
                    .param("first", parts[1])
                    .param("last", parts[2])
                    .param("department", profile.department())
                    .param("college", profile.college())
                    .param("education", profile.education())
                    .param("specialDuty", profile.specialDuty())
                    .query(Long.class)
                    .single();
            ids.put(fullName, id);
        });
        return ids;
    }

    /** ข้อมูลหัวตารางของครูหนึ่งคน รวมจากทุกใบที่เจอชื่อนี้ */
    private record Profile(String department, String college, String education, String specialDuty) {

        static Profile of(ScheduleJson doc) {
            return new Profile(blankToNull(doc.major()), blankToNull(doc.college()),
                    blankToNull(doc.education()), blankToNull(doc.specialDuty()));
        }

        Profile fillFrom(Profile other) {
            return new Profile(
                    department == null ? other.department() : department,
                    college == null ? other.college() : college,
                    education == null ? other.education() : education,
                    specialDuty == null ? other.specialDuty() : specialDuty);
        }
    }

    /**
     * ชื่อต้นที่ชี้ครูได้คนเดียวเท่านั้นถึงจะตั้ง alias ให้อัตโนมัติ
     * ซ้ำเมื่อไรปล่อยว่างไว้ให้คนตัดสิน ห้ามระบบเดา (V1__core.sql "หัวใจของระบบ")
     */
    private void upsertAliases(List<ScheduleJson> docs, Map<String, Long> teacherIds, List<String> warnings) {
        Map<String, Set<Long>> byFirstName = new LinkedHashMap<>();
        teacherIds.forEach((fullName, id) ->
                byFirstName.computeIfAbsent(splitName(fullName)[1], k -> new HashSet<>()).add(id));

        byFirstName.forEach((firstName, ids) -> {
            if (ids.size() > 1) {
                warnings.add("ชื่อต้น \"" + firstName + "\" ตรงกับครู " + ids.size()
                        + " คน ไม่ตั้ง alias ให้อัตโนมัติ ต้องให้คนจับคู่");
                return;
            }
            jdbc.sql("""
                            insert into teacher_alias (alias_text, teacher_id) values (:alias, :teacherId)
                            on conflict (alias_text) do nothing
                            """)
                    .param("alias", firstName)
                    .param("teacherId", ids.iterator().next())
                    .update();
        });
    }

    /** ล้างคาบเดิมของครูในไฟล์นี้แล้วใส่ใหม่ ทำให้รันซ้ำได้โดยไม่เกิดแถวซ้ำ */
    private int replaceSessions(List<ScheduleJson> docs, Map<String, Long> teacherIds, List<String> warnings) {
        if (teacherIds.isEmpty()) {
            return 0;
        }
        jdbc.sql("""
                        delete from session where id in (
                            select session_id from session_teacher where teacher_id in (:ids))
                        """)
                .param("ids", teacherIds.values())
                .update();

        int inserted = 0;
        for (ScheduleJson doc : docs) {
            Long teacherId = teacherIds.get(doc.teacher().trim());
            String alias = splitName(doc.teacher().trim())[1];

            for (ScheduleJson.Slot slot : slots(doc)) {
                Integer day = slot.day() == null ? null : DAYS.get(slot.day().trim());
                // ชั่วโมงต้องอยู่ใน 0-23 ด้วย ไฟล์มาจากตัวสกัดภายนอก ค่าหลุดช่วงต้องตกไปพร้อมคำเตือน
                // ไม่ใช่โยน exception จนทั้งไฟล์เข้าไม่ได้
                if (day == null || slot.start() == null || slot.end() == null
                        || slot.start() < 0 || slot.end() > 23 || slot.end() <= slot.start()) {
                    warnings.add("คาบเสียรูป ข้ามไป (" + doc.sourceFile() + "): "
                            + slot.day() + " " + slot.start() + "-" + slot.end());
                    continue;
                }
                Long sessionId = jdbc.sql("""
                                insert into session (semester, day_of_week, start_time, end_time, kind,
                                    subject_code, room_code, group_code, week_from, week_to,
                                    source_image, source_note)
                                values (:semester, :day, :start, :end, :kind,
                                    :subject, :room, :grp, :weekFrom, :weekTo, :sourceImage, :sourceNote)
                                returning id
                                """)
                        .param("semester", doc.semester())
                        .param("day", day)
                        .param("start", LocalTime.of(slot.start(), 0))
                        .param("end", LocalTime.of(slot.end(), 0))
                        .param("kind", slot.kind() == null ? "ทฤษฎี" : slot.kind())
                        .param("subject", slot.code())
                        .param("room", blankToNull(slot.room()))
                        .param("grp", blankToNull(slot.group()))
                        .param("weekFrom", doc.resolvedWeekFrom())
                        .param("weekTo", doc.resolvedWeekTo())
                        .param("sourceImage", blankToNull(doc.sourceFile()))
                        .param("sourceNote", blankToNull(doc.note()))
                        .query(Long.class)
                        .single();

                jdbc.sql("""
                                insert into session_teacher (session_id, ordinal, alias_text, teacher_id)
                                values (:sessionId, 1, :alias, :teacherId)
                                """)
                        .param("sessionId", sessionId)
                        .param("alias", alias)
                        .param("teacherId", teacherId)
                        .update();
                inserted++;
            }
        }
        return inserted;
    }

    private static List<ScheduleJson.Slot> slots(ScheduleJson doc) {
        return doc.slots() == null ? List.of() : doc.slots();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** "นางสาววิชยวรรณ ปิ่นโพธิ์" -> [นางสาว, วิชยวรรณ, ปิ่นโพธิ์] */
    static String[] splitName(String fullName) {
        String rest = fullName.trim();
        String prefix = null;
        for (String candidate : PREFIXES) {
            if (rest.startsWith(candidate)) {
                prefix = candidate;
                rest = rest.substring(candidate.length()).trim();
                break;
            }
        }
        String[] words = rest.split("\\s+", 2);
        String first = words[0];
        String last = words.length > 1 ? words[1] : "";
        return new String[]{prefix, first, last};
    }

    /** teacher.code ต้อง unique และคงที่ทุกครั้งที่ import ซ้ำ จึงผูกกับชื่อเต็มแทนการนับลำดับ */
    static String teacherCode(String fullName) {
        return "T%08X".formatted(fullName.trim().hashCode());
    }
}
