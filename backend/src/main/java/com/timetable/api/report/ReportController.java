package com.timetable.api.report;

import com.timetable.api.auth.RequireRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * รายงานที่ใช้ส่งอาจารย์ (docs/แผนงาน.md ข้อ 6.6)
 * ตัวเลขทุกตัวคำนวณสดจากตารางจริง ไม่มี dataset แยกและไม่มีค่าที่กรอกเอง
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final JdbcClient jdbc;

    public ReportController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record FieldAccuracy(String field, double accuracy, long n) {
    }

    public record OcrAccuracy(List<FieldAccuracy> byField, long totalReviewed, String note) {
    }

    /**
     * ความแม่นรายฟิลด์ตามสูตรข้อ 10:
     * field_accuracy(f) = COUNT(ocr_value = human_value) / COUNT(*) WHERE field = f
     * ตัวหารคือจำนวนช่องที่คนตรวจทั้งหมด ตัวเศษคือช่องที่ OCR อ่านตรงกับที่คนยืนยัน
     */
    @GetMapping("/ocr-accuracy")
    @RequireRole({"staff", "teacher"})
    public OcrAccuracy ocrAccuracy() {
        List<FieldAccuracy> rows = jdbc.sql("""
                        select field_name,
                               count(*)                                                        as n,
                               sum(case when old_value is not distinct from new_value then 1 else 0 end) as hit
                        from field_correction
                        group by field_name
                        order by field_name
                        """)
                .query((rs, rowNum) -> {
                    long n = rs.getLong("n");
                    long hit = rs.getLong("hit");
                    return new FieldAccuracy(rs.getString("field_name"), n == 0 ? 0 : (double) hit / n, n);
                })
                .list();

        long total = rows.stream().mapToLong(FieldAccuracy::n).sum();
        String note = total == 0
                ? "ยังไม่มีการตรวจทาน ตัวเลขจะเกิดขึ้นเมื่อเจ้าหน้าที่ยืนยันช่องในหน้าตรวจทาน"
                : "คิดจากช่องที่ผ่านการตรวจทานแล้ว " + total + " ช่อง";
        return new OcrAccuracy(rows, total, note);
    }

    public record ToolUse(String tool, long n) {
    }

    public record ChatQuality(long answers,
                              Long p95LatencyMs,
                              Long medianLatencyMs,
                              double unansweredRate,
                              double clarifyRate,
                              List<ToolUse> byTool,
                              String note) {
    }

    /**
     * คุณภาพคำตอบของชั้นแชท ตอนนี้ทุกคำตอบมาจาก SQL tool (source="tool")
     * ยังไม่มี verificationPassRate เพราะยังไม่มีคำตอบจากโมเดลให้ตรวจ (ADR-10)
     */
    @GetMapping("/chat-quality")
    @RequireRole({"staff", "teacher"})
    public ChatQuality chatQuality() {
        Long answers = jdbc.sql("select count(*) from chat_message where role = 'assistant'")
                .query(Long.class).single();

        Long p95 = jdbc.sql("""
                        select cast(percentile_disc(0.95) within group (order by latency_ms) as bigint)
                        from chat_message where role = 'assistant' and latency_ms is not null
                        """).query(Long.class).optional().orElse(null);

        Long median = jdbc.sql("""
                        select cast(percentile_disc(0.5) within group (order by latency_ms) as bigint)
                        from chat_message where role = 'assistant' and latency_ms is not null
                        """).query(Long.class).optional().orElse(null);

        // ไม่มี tool_calls = ตอบไม่ได้ ต้องนับแยกเพราะเป็นตัวชี้ว่า dataset/ตรรกะยังครอบไม่ถึงคำถามแบบไหน
        Long unanswered = jdbc.sql(
                        "select count(*) from chat_message where role = 'assistant' and tool_calls is null")
                .query(Long.class).single();

        Long clarify = jdbc.sql("""
                        select count(*) from chat_message
                        where role = 'assistant' and tool_calls ->> 'tool' = 'clarify'
                        """).query(Long.class).single();

        List<ToolUse> byTool = jdbc.sql("""
                        select coalesce(tool_calls ->> 'tool', 'ตอบไม่ได้') as tool, count(*) as n
                        from chat_message where role = 'assistant'
                        group by 1 order by n desc
                        """)
                .query((rs, rowNum) -> new ToolUse(rs.getString("tool"), rs.getLong("n")))
                .list();

        double unansweredRate = answers == 0 ? 0 : (double) unanswered / answers;
        double clarifyRate = answers == 0 ? 0 : (double) clarify / answers;
        return new ChatQuality(answers, p95, median, unansweredRate, clarifyRate, byTool,
                "ทุกคำตอบมาจาก SQL tool โดยตรง ยังไม่มีชั้นโมเดลให้ตรวจ verification");
    }
}
