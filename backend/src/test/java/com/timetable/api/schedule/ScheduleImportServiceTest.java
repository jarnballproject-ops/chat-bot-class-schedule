package com.timetable.api.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * ชื่อครูในไฟล์เป็นสตริงเดียวคำนำหน้าติดกับชื่อต้น แยกผิดแปลว่าแชทค้นชื่อไม่เจอ
 * และ teacher.code ต้องคงที่ ไม่งั้น import ซ้ำจะได้ครูซ้ำคนเดิมสองแถว
 */
class ScheduleImportServiceTest {

    @Test
    void splitsPrefixFromThaiName() {
        assertArrayEquals(new String[]{"นางสาว", "วิชยวรรณ", "ปิ่นโพธิ์"},
                ScheduleImportService.splitName("นางสาววิชยวรรณ ปิ่นโพธิ์"));
        assertArrayEquals(new String[]{"นาย", "ปิติภัทร", "ศรีคำภา"},
                ScheduleImportService.splitName("นายปิติภัทร ศรีคำภา"));
        assertArrayEquals(new String[]{"นาง", "สจี", "พรหมมาศ"},
                ScheduleImportService.splitName("นางสจี พรหมมาศ"));
    }

    /** "นางสาว" ต้องชนะ "นาง" ไม่งั้นชื่อต้นจะกลายเป็น "สาววิชยวรรณ" */
    @Test
    void prefersLongestPrefix() {
        assertEquals("วิชยวรรณ", ScheduleImportService.splitName("นางสาววิชยวรรณ ปิ่นโพธิ์")[1]);
    }

    @Test
    void handlesNameWithoutPrefixOrLastName() {
        assertArrayEquals(new String[]{null, "สมชาย", "ใจดี"}, ScheduleImportService.splitName("สมชาย ใจดี"));
        assertArrayEquals(new String[]{"นาย", "สมชาย", ""}, ScheduleImportService.splitName("นายสมชาย"));
    }

    @Test
    void teacherCodeIsStableAndDistinct() {
        assertEquals(ScheduleImportService.teacherCode("นายปิติภัทร ศรีคำภา"),
                ScheduleImportService.teacherCode("  นายปิติภัทร ศรีคำภา  "));
        assertNotEquals(ScheduleImportService.teacherCode("นายปิติภัทร ศรีคำภา"),
                ScheduleImportService.teacherCode("นางสจี พรหมมาศ"));
    }
}
