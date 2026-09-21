package com.timetable.api.session;

import java.time.LocalTime;

/**
 * ฟิลด์ที่หน้าแก้ตารางส่งมาได้ ค่า null = ไม่แก้ฟิลด์นั้น
 * subjectName มาคู่กับ subjectCode เพราะชื่อวิชาอยู่ตาราง subject ไม่ได้อยู่ใน session
 */
public record SessionPatch(
        Short dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String kind,
        String subjectCode,
        String subjectName,
        String roomCode,
        String groupCode) {
}
