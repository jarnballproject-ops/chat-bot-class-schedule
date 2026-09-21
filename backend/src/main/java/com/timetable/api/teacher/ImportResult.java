package com.timetable.api.teacher;

import java.util.List;

/** ผลนำเข้า CSV — แถวเสียถูกรายงานกลับพร้อมเลขบรรทัด ไม่ล้มทั้งไฟล์ */
public record ImportResult(int inserted, int updated, List<Rejected> rejected) {

    public record Rejected(long line, String reason) {
    }
}
