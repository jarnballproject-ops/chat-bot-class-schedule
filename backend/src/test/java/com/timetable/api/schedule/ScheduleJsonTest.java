package com.timetable.api.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ไฟล์ชุดใหม่เขียนช่วงสัปดาห์เป็น "4-18" แทน weekFrom/weekTo
 * แกะพลาดแปลว่าคาบของครูคนเดียวกันทุกใบจะซ้อนกันในตารางเดียว
 */
class ScheduleJsonTest {

    private static ScheduleJson doc(String weekRange, Integer from, Integer to) {
        return new ScheduleJson(null, "1/2569", null, null, "นางสจี พรหมมาศ", null, null, null,
                weekRange, from, to, null, null, null, null);
    }

    @Test
    void readsWeekRangeString() {
        assertEquals(4, doc("4-18", null, null).resolvedWeekFrom());
        assertEquals(18, doc("4-18", null, null).resolvedWeekTo());
        assertEquals(17, doc(" 17 – 18 ", null, null).resolvedWeekFrom());
        assertEquals(18, doc(" 17 – 18 ", null, null).resolvedWeekTo());
    }

    /** ใบที่ใช้สัปดาห์เดียวเขียนเลขตัวเดียว ต้องได้ช่วงปิดหัวปิดท้ายเท่ากัน ไม่ใช่ปลายเปิด */
    @Test
    void singleWeekBecomesClosedRange() {
        assertEquals(7, doc("7", null, null).resolvedWeekFrom());
        assertEquals(7, doc("7", null, null).resolvedWeekTo());
    }

    @Test
    void keepsExplicitFieldsAndSurvivesGarbage() {
        assertEquals(1, doc("4-18", 1, 3).resolvedWeekFrom());
        assertEquals(3, doc("4-18", 1, 3).resolvedWeekTo());
        assertNull(doc("ไม่ระบุ", null, null).resolvedWeekFrom());
        assertNull(doc(null, null, null).resolvedWeekTo());
    }
}
