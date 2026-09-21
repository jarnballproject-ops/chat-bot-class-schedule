package com.timetable.api.schedule;

import java.time.LocalTime;

/**
 * หนึ่งคาบสอนที่อ่านจาก view v_teacher_schedule
 * เป็น interface projection ไม่ใช่ entity เพราะ view ไม่มี primary key เดี่ยว
 * (คาบที่สอนร่วมกันจะมี session_id ซ้ำหลายแถว)
 */
public interface TeacherScheduleRow {

    Long getSessionId();

    String getSemester();

    Short getDayOfWeek();

    LocalTime getStartTime();

    LocalTime getEndTime();

    String getKind();

    String getSubjectCode();

    String getSubjectName();

    String getRoomCode();

    String getGroupCode();

    Integer getHeadcount();
}
