package com.timetable.api.chat;

import com.timetable.api.chat.ChatMessages.ChatReply;
import com.timetable.api.chat.ChatMessages.Clarify;
import com.timetable.api.chat.ChatMessages.Option;
import com.timetable.api.chat.ChatMessages.Verification;
import com.timetable.api.llm.LlmClient;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ชั้นตอบคำถามแบบ SQL tool (docs/แผนงาน.md ข้อ 7)
 * สไลซ์นี้ยังไม่มี sidecar โมเดล คำตอบทุกข้อความจึงเป็น source="tool" ตรงจาก Ready Data
 * เมื่อ adapter พร้อม ให้เรียกโมเดลก่อน แล้วใช้เมธอดในคลาสนี้เป็น verification + fallback
 */
@Service
public class ChatService {

    private static final Map<String, Integer> DAY_WORDS = new LinkedHashMap<>();

    static {
        DAY_WORDS.put("จันทร์", 1);
        DAY_WORDS.put("อังคาร", 2);
        DAY_WORDS.put("พุธ", 3);
        DAY_WORDS.put("พฤหัสบดี", 4);
        DAY_WORDS.put("พฤหัส", 4);
        DAY_WORDS.put("ศุกร์", 5);
        DAY_WORDS.put("เสาร์", 6);
        DAY_WORDS.put("อาทิตย์", 7);
    }

    private static final String[] DAY_NAMES =
            {"จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์", "อาทิตย์"};

    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    private static final LocalTime DAY_END = LocalTime.of(19, 0);

    /**
     * คำสั่งระบบ ใช้ข้อความเดียวกับฟิลด์ instruction ใน dataset/schedule_chatbot_train.jsonl
     * เพื่อให้ adapter ที่เทรนมาเจอ prompt หน้าตาเดิมกับตอนเทรน แล้วต่อท้ายด้วยกฎกันมั่ว
     */
    private static final String SYSTEM_PROMPT = """
            ตอบคำถามเกี่ยวกับตารางสอนของอาจารย์จากข้อมูลที่กำหนด
            กติกา: ใช้ข้อมูลใน JSON เท่านั้น ห้ามเดาและห้ามแต่งเพิ่ม ถ้าข้อมูลไม่พอให้บอกว่าไม่มีข้อมูล
            ตอบเป็นภาษาไทย สั้น ตรงคำถาม เขียนเวลาเป็นรูปแบบ HH:MM
            ใช้รหัสวิชา ชื่อห้อง และรหัสกลุ่มตามที่ปรากฏใน JSON เท่านั้น
            """;

    private final JdbcClient jdbc;
    private final LlmClient llm;
    private final ScheduleContextBuilder contexts;

    public ChatService(JdbcClient jdbc, LlmClient llm, ScheduleContextBuilder contexts) {
        this.jdbc = jdbc;
        this.llm = llm;
        this.contexts = contexts;
    }

    public ChatReply answer(String conversationId, String text, Long teacherId) {
        long started = System.currentTimeMillis();
        String id = safeConversationId(conversationId);
        String question = text == null ? "" : text.trim();
        persist(id, "user", question, null, null);

        if (question.isEmpty()) {
            return reply(id, "พิมพ์คำถามเกี่ยวกับตารางสอนได้เลย เช่น \"อ.สจี วันอังคารสอนอะไร\"",
                    null, List.of(), started);
        }

        // ผู้ใช้กดเลือกตัวคนมาแล้ว ไม่ต้องเดาจากชื่อในข้อความอีก ไม่งั้นจะวนถามกลับไม่รู้จบ
        // เพราะข้อความที่ส่งกลับมายังมีชื่อต้นที่ซ้ำกันอยู่
        if (teacherId != null) {
            Option picked = findTeacher(teacherId);
            if (picked == null) {
                return reply(id, "ไม่พบอาจารย์ที่เลือก ลองถามใหม่อีกครั้ง", null, List.of(), started);
            }
            return answerForTeacher(id, question, picked, started);
        }

        List<Option> matched = matchTeachers(question);
        if (matched.size() > 1) {
            String ask = "ชื่อนี้ตรงกับอาจารย์มากกว่าหนึ่งคน ช่วยเลือกก่อน";
            long latency = System.currentTimeMillis() - started;
            persist(id, "assistant", ask, "clarify", List.of(), latency);
            return new ChatReply(id, ask, "tool", null, List.of(),
                    new Clarify("หมายถึงอาจารย์ท่านไหน", matched), Verification.NOT_CHECKED, latency);
        }
        if (matched.size() == 1) {
            return answerForTeacher(id, question, matched.getFirst(), started);
        }

        List<Row> subjectRows = matchSubject(question);
        if (!subjectRows.isEmpty()) {
            return reply(id, subjectText(subjectRows), "subjectWhere", ids(subjectRows), started);
        }

        return reply(id, "ยังตอบคำถามนี้ไม่ได้ ลองระบุชื่ออาจารย์หรือชื่อวิชา เช่น \"อ.สจี วันจันทร์สอนอะไร\" "
                + "หรือ \"การสร้างสื่อดิจิทัล เรียนที่ไหน\"", null, List.of(), started);
    }

    private ChatReply answerForTeacher(String id, String question, Option teacher, long started) {
        Integer day = matchDay(question);
        List<Row> rows = schedule(teacher.teacherId(), day);
        boolean askingFree = question.contains("ว่าง");
        String tool = askingFree ? freeText(teacher, day, rows) : scheduleText(teacher, day, rows);
        String toolName = askingFree ? "teacherFree" : "teacherDay";

        ChatReply fromModel = tryModel(id, question, teacher, rows, tool, toolName, started);
        return fromModel != null ? fromModel : reply(id, tool, toolName, ids(rows), started);
    }

    /**
     * ให้โมเดลอ่านตารางทั้งสัปดาห์ของครูคนนั้นเป็น JSON แล้วเรียบเรียงคำตอบเอง
     * ตัวเลข เวลา รหัสวิชา และกลุ่มต้องผ่าน AnswerVerifier ก่อนเสมอ ไม่ผ่าน = คืน null ให้ใช้ SQL แทน
     * คืน null ด้วยเมื่อปิดโมเดลไว้ sidecar ล่ม หรือครูคนนั้นไม่มีคาบสอนเลย
     */
    private ChatReply tryModel(String id, String question, Option teacher, List<Row> rows,
                               String tool, String toolName, long started) {
        if (!llm.enabled()) {
            return null;
        }
        ScheduleContext context = contexts.forTeacher(teacher.teacherId(), teacher.label());
        if (context.empty()) {
            return null;
        }

        Optional<String> answer = llm.complete(SYSTEM_PROMPT, context.json() + "\n\nคำถาม: " + question);
        if (answer.isEmpty()) {
            return null;
        }

        List<String> mismatched = AnswerVerifier.verify(answer.get(), context);
        long latency = System.currentTimeMillis() - started;
        if (!mismatched.isEmpty()) {
            // คำตอบโมเดลผิดข้อเท็จจริง ทิ้งทิ้งไปแล้วส่งคำตอบจาก SQL แทน แต่บันทึกไว้ว่าทำไมถึงตก
            persist(id, "assistant", tool, toolName + "-fallback", ids(rows), latency);
            return new ChatReply(id, tool, "tool", llm.version(), ids(rows), null,
                    new Verification(true, false, mismatched), latency);
        }
        persist(id, "assistant", answer.get(), toolName + "-model", ids(rows), latency);
        return new ChatReply(id, answer.get(), "model", llm.version(), ids(rows), null,
                new Verification(true, true, List.of()), latency);
    }

    private Option findTeacher(Long teacherId) {
        return jdbc.sql("""
                        select id, coalesce(prefix, '') || first_name || ' ' || last_name as label
                        from teacher where id = :id
                        """)
                .param("id", teacherId)
                .query((rs, rowNum) -> new Option(rs.getLong("id"), rs.getString("label")))
                .optional().orElse(null);
    }

    /**
     * ชื่อต้นที่ชี้ครูได้หลายคนต้องถามกลับ ห้ามเดา (กฎข้อ 4 ของชั้นแชท)
     * แต่ถ้าผู้ใช้พิมพ์นามสกุลมาด้วย ถือว่าเจาะจงแล้ว ตัดคนที่นามสกุลไม่ตรงทิ้งก่อนนับ
     * ไม่งั้น "ไมตรี นาโพธิ์" จะยังโดนถามกลับเพราะมีครูชื่อไมตรีหลายคน
     */
    private List<Option> matchTeachers(String question) {
        List<Match> matched = jdbc.sql("""
                        select distinct t.id, coalesce(t.prefix, '') || t.first_name || ' ' || t.last_name as label,
                               t.last_name
                        from teacher t
                                 left join teacher_alias a on a.teacher_id = t.id
                        where position(t.first_name in :q) > 0
                           or (a.alias_text is not null and position(a.alias_text in :q) > 0)
                        """)
                .param("q", question)
                .query((rs, rowNum) ->
                        new Match(new Option(rs.getLong("id"), rs.getString("label")), rs.getString("last_name")))
                .list();

        List<Match> byLastName = matched.stream()
                .filter(m -> m.lastName() != null && question.contains(m.lastName()))
                .toList();
        List<Match> narrowed = byLastName.isEmpty() ? matched : byLastName;
        return narrowed.stream().map(Match::option).toList();
    }

    private record Match(Option option, String lastName) {
    }

    private Integer matchDay(String question) {
        if (question.contains("วันนี้")) {
            return LocalDate.now().getDayOfWeek().getValue();
        }
        if (question.contains("พรุ่งนี้")) {
            return LocalDate.now().plusDays(1).getDayOfWeek().getValue();
        }
        if (question.contains("เมื่อวาน")) {
            return LocalDate.now().minusDays(1).getDayOfWeek().getValue();
        }
        for (Map.Entry<String, Integer> entry : DAY_WORDS.entrySet()) {
            if (question.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<Row> schedule(Long teacherId, Integer day) {
        return jdbc.sql("""
                        select session_id, day_of_week, start_time, end_time, subject_code, subject_name,
                               room_code, group_code, teacher_name
                        from v_teacher_schedule
                        where teacher_id = :teacherId
                          and (cast(:day as int) is null or day_of_week = cast(:day as int))
                          and status = 'published'
                        order by day_of_week, start_time
                        """)
                .param("teacherId", teacherId)
                .param("day", day)
                .query(Row.MAPPER)
                .list();
    }

    private List<Row> matchSubject(String question) {
        return jdbc.sql("""
                        select session_id, day_of_week, start_time, end_time, subject_code, subject_name,
                               room_code, group_code, teacher_name
                        from v_teacher_schedule
                        where status = 'published'
                          and (position(subject_code in :q) > 0
                               or (subject_name is not null and position(subject_name in :q) > 0))
                        order by day_of_week, start_time
                        """)
                .param("q", question)
                .query(Row.MAPPER)
                .list();
    }

    private String scheduleText(Option teacher, Integer day, List<Row> rows) {
        String when = day != null ? "วัน" + DAY_NAMES[day - 1] : "สัปดาห์นี้";
        if (rows.isEmpty()) {
            return teacher.label() + " " + when + " ไม่มีคาบสอนในตารางที่เผยแพร่แล้ว";
        }
        StringBuilder text = new StringBuilder(teacher.label() + " " + when + " สอน " + rows.size() + " คาบ");
        for (Row row : rows) {
            text.append("\n· ").append(day == null ? "วัน" + DAY_NAMES[row.day() - 1] + " " : "")
                    .append(hhmm(row.start())).append("–").append(hhmm(row.end()))
                    .append(" ").append(row.subjectName() == null ? row.subjectCode() : row.subjectName())
                    .append(" (").append(row.subjectCode()).append(")")
                    .append(" ห้อง ").append(row.room())
                    .append(" กลุ่ม ").append(row.group());
        }
        return text.toString();
    }

    private String freeText(Option teacher, Integer day, List<Row> rows) {
        if (day == null) {
            return "ถามช่วงว่างต้องระบุวันด้วย เช่น \"" + teacher.label() + " วันพุธว่างไหม\"";
        }
        List<String> free = new ArrayList<>();
        LocalTime cursor = DAY_START;
        for (Row row : rows) {
            if (row.start().isAfter(cursor)) {
                free.add(hhmm(cursor) + "–" + hhmm(row.start()));
            }
            if (row.end().isAfter(cursor)) {
                cursor = row.end();
            }
        }
        if (cursor.isBefore(DAY_END)) {
            free.add(hhmm(cursor) + "–" + hhmm(DAY_END));
        }
        String when = "วัน" + DAY_NAMES[day - 1];
        if (rows.isEmpty()) {
            return teacher.label() + " " + when + " ไม่มีคาบสอนเลย ว่างทั้งวัน";
        }
        return free.isEmpty()
                ? teacher.label() + " " + when + " สอนเต็มทั้งวัน ไม่มีช่วงว่าง"
                : teacher.label() + " " + when + " ว่างช่วง " + String.join(", ", free);
    }

    private String subjectText(List<Row> rows) {
        Row first = rows.getFirst();
        StringBuilder text = new StringBuilder(
                (first.subjectName() == null ? first.subjectCode() : first.subjectName())
                        + " (" + first.subjectCode() + ") มี " + rows.size() + " คาบ");
        for (Row row : rows) {
            text.append("\n· วัน").append(DAY_NAMES[row.day() - 1]).append(" ")
                    .append(hhmm(row.start())).append("–").append(hhmm(row.end()))
                    .append(" ห้อง ").append(row.room())
                    .append(" กลุ่ม ").append(row.group())
                    .append(row.teacherName() == null ? " (ยังไม่จับคู่ครู)" : " สอนโดย " + row.teacherName());
        }
        return text.toString();
    }

    private ChatReply reply(String id, String answer, String tool, List<Long> citations, long started) {
        long latency = System.currentTimeMillis() - started;
        persist(id, "assistant", answer, tool, citations, latency);
        return new ChatReply(id, answer, "tool", null, citations, null, Verification.NOT_CHECKED, latency);
    }

    /**
     * เก็บทุกข้อความพร้อม tool ที่ใช้และเวลาที่ใช้ตอบ เพื่อให้ตอบได้ว่าคำตอบมาจากข้อมูลไหน
     * และเป็นแหล่งเดียวของตัวเลขใน /reports/chat-quality (docs/แผนงาน.md ข้อ 5, 6.6)
     */
    private void persist(String conversationId, String role, String text, String tool, List<Long> citations) {
        persist(conversationId, role, text, tool, citations, null);
    }

    private void persist(String conversationId, String role, String text,
                         String tool, List<Long> citations, Long latencyMs) {
        // tool กับ citations เป็นค่าที่ระบบสร้างเอง (ชื่อคงที่ + id ตัวเลข) ไม่มีข้อความผู้ใช้ปนใน JSON
        String toolCalls = tool == null ? null
                : "{\"tool\":\"" + tool + "\",\"citations\":" + (citations == null ? "[]" : citations) + "}";
        jdbc.sql("""
                        insert into chat_message (conversation_id, role, text, tool_calls, latency_ms)
                        values (cast(? as uuid), ?, ?, cast(? as jsonb), ?)
                        """)
                .params(conversationId, role, text, toolCalls, latencyMs)
                .update();
    }

    /** client ส่งอะไรมาก็ได้ แต่คอลัมน์เป็น uuid ถ้าแปลงไม่ได้ให้เริ่มบทสนทนาใหม่แทนที่จะพัง */
    private static String safeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        try {
            return UUID.fromString(conversationId.trim()).toString();
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID().toString();
        }
    }

    private static List<Long> ids(List<Row> rows) {
        return rows.stream().map(Row::sessionId).toList();
    }

    private static String hhmm(LocalTime time) {
        return time.toString().substring(0, 5);
    }

    private record Row(Long sessionId, int day, LocalTime start, LocalTime end, String subjectCode,
                       String subjectName, String room, String group, String teacherName) {

        static final RowMapper<Row> MAPPER = (rs, rowNum) -> new Row(
                rs.getLong("session_id"),
                rs.getInt("day_of_week"),
                rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time").toLocalTime(),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                rs.getString("room_code"),
                rs.getString("group_code"),
                rs.getString("teacher_name"));
    }
}
