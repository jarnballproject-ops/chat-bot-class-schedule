package com.timetable.api.chat;

import java.util.List;

/** สัญญาข้อความของ POST /chat ตาม docs/แผนงาน.md ข้อ 6.4 (สไลซ์นี้ยังตอบจาก SQL tool เท่านั้น) */
public final class ChatMessages {

    /** teacherId มาจากปุ่มที่ผู้ใช้กดตอนระบบถามกลับ ใช้ชี้ตัวคนแทนการเดาจากชื่อในข้อความ */
    public record ChatRequest(String conversationId, String text, Long teacherId) {
    }

    public record Option(Long teacherId, String label) {
    }

    public record Clarify(String question, List<Option> options) {
    }

    /**
     * ผลการตรวจคำตอบของโมเดลเทียบ SQL จริง
     * checked=false แปลว่าคำตอบไม่ได้มาจากโมเดล จึงไม่ต้องตรวจ
     */
    public record Verification(boolean checked, boolean passed, List<String> mismatched) {

        public static final Verification NOT_CHECKED = new Verification(false, false, List.of());
    }

    /** source: model = โมเดลตอบและผ่านการตรวจ · tool = ตอบจาก SQL ตรง (รวมกรณี fallback) */
    public record ChatReply(String conversationId,
                            String answer,
                            String source,
                            String modelVersion,
                            List<Long> citations,
                            Clarify clarify,
                            Verification verification,
                            long latencyMs) {
    }

    private ChatMessages() {
    }
}
