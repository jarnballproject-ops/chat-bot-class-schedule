package com.timetable.api.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** กันโมเดลมั่วเวลา รหัสวิชา และกลุ่มเรียน ซึ่งเป็นสามอย่างที่ผิดแล้วคนเชื่อทันที */
class AnswerVerifierTest {

    private static final ScheduleContext CONTEXT = ScheduleContext.of(
            "{}", List.of(10, 12, 14, 16), List.of("31900-1003", "31901-2009"), List.of("สท.4/1", "คภ.5/1-2"));

    @Test
    void passesWhenEveryFactExistsInSchedule() {
        String answer = "วันจันทร์ 10:00–12:00 การสร้างสื่อดิจิทัล (31900-1003) ห้อง COM404 กลุ่ม สท.4/1";

        assertTrue(AnswerVerifier.verify(answer, CONTEXT).isEmpty());
    }

    @Test
    void rejectsTimeThatIsNotAPeriodBoundary() {
        List<String> mismatched = AnswerVerifier.verify("สอน 09:30–12:00", CONTEXT);

        assertEquals(List.of("เวลา 09:30"), mismatched);
    }

    @Test
    void rejectsSubjectCodeAndGroupThatAreNotInSchedule() {
        List<String> mismatched = AnswerVerifier.verify("วิชา 99999-1111 กลุ่ม สท.9/9", CONTEXT);

        assertEquals(List.of("รหัสวิชา 99999-1111", "กลุ่มเรียน สท.9/9"), mismatched);
    }

    @Test
    void rejectsEmptyAnswer() {
        assertEquals(List.of("คำตอบว่าง"), AnswerVerifier.verify("   ", CONTEXT));
    }
}
