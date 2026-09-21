package com.timetable.api.schedule;

import java.util.List;
import java.util.Map;

/**
 * รูปร่างของ dataset/raw/schedules_*.json หนึ่ง record = ตารางสอนหนึ่งใบ (หนึ่งรูป)
 * ครูคนเดียวมีได้หลายใบต่อภาคเรียน แยกกันด้วยช่วงสัปดาห์
 *
 * <p>ไฟล์ชุดใหม่ (schedules_*_1.json) เขียนช่วงสัปดาห์เป็นสตริงเดียว "4-18" แทน weekFrom/weekTo
 * จึงรับทั้งสองแบบ ไฟล์ชุดเก่ายังนำเข้าได้โดยไม่ต้องแปลงก่อน
 * ฟิลด์ที่ยังไม่ได้ใช้ (sourceImageId, recordType, classGroup) ปล่อยให้ Jackson ข้ามไป
 */
public record ScheduleJson(
        String sourceFile,
        String semester,
        String college,
        String major,
        String teacher,
        String education,
        String specialDuty,
        String note,
        String weekRange,
        Integer weekFrom,
        Integer weekTo,
        Map<String, Subject> subjects,
        Map<String, Integer> groups,
        Map<String, List<String>> combinedGroups,
        List<Slot> slots) {

    public record Subject(String name, Integer theory, Integer practice, Integer credits) {
    }

    /** start/end เป็นชั่วโมงเต็ม (8-20) ตามที่ปรากฏในไฟล์ */
    public record Slot(String day, Integer start, Integer end, String code,
                       String kind, String room, String group) {
    }

    /** สัปดาห์แรกที่ตารางใบนี้ใช้ อ่านจาก weekFrom ก่อน ไม่มีค่อยแกะจาก weekRange */
    public Integer resolvedWeekFrom() {
        return weekFrom != null ? weekFrom : weekPart(0);
    }

    /** สัปดาห์สุดท้าย "17" เดี่ยว ๆ หมายถึงใบนั้นใช้สัปดาห์เดียว จึงคืนค่าเดียวกับ from */
    public Integer resolvedWeekTo() {
        Integer to = weekTo != null ? weekTo : weekPart(1);
        return to != null ? to : resolvedWeekFrom();
    }

    private Integer weekPart(int index) {
        if (weekRange == null || weekRange.isBlank()) {
            return null;
        }
        String[] parts = weekRange.trim().split("\\s*[-–—]\\s*");
        if (index >= parts.length) {
            return null;
        }
        try {
            return Integer.valueOf(parts[index].trim());
        } catch (NumberFormatException e) {
            // ค่าที่แกะไม่ได้ต้องกลายเป็น "ไม่รู้ช่วงสัปดาห์" ไม่ใช่ทำให้ทั้งไฟล์เข้าไม่ได้
            return null;
        }
    }
}
