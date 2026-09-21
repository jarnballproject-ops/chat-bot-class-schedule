package com.timetable.api.schedule;

import java.util.List;

/** สรุปผลนำเข้าหนึ่งไฟล์ warnings คือรายการที่ข้ามหรือเติมค่าแทนให้ ต้องมีคนไล่ดู */
public record ScheduleImportResult(
        String file,
        int documents,
        int imported,
        int teachers,
        int sessions,
        List<String> warnings) {
}
