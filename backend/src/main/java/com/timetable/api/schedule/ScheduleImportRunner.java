package com.timetable.api.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * นำเข้าตารางจากไฟล์ JSON ตอนสตาร์ท เพิ่มแผนกใหม่ = วางไฟล์แล้วรีสตาร์ต backend
 * ไม่ตั้งค่า APP_SCHEDULE_IMPORT_FILE หรือหาไฟล์ไม่เจอ = ข้ามไป ระบบยังขึ้นได้ตามปกติ
 */
@Component
public class ScheduleImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleImportRunner.class);

    private final ScheduleImportService importer;
    private final String file;

    public ScheduleImportRunner(ScheduleImportService importer,
                                @Value("${app.schedule-import.file:}") String file) {
        this.importer = importer;
        this.file = file;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (file == null || file.isBlank()) {
            return;
        }
        Path path = Path.of(file);
        if (!Files.isRegularFile(path)) {
            log.warn("ข้ามการนำเข้าตาราง: ไม่พบไฟล์ {}", path.toAbsolutePath());
            return;
        }
        try {
            ScheduleImportResult result = importer.importFile(path);
            result.warnings().forEach(warning -> log.warn("นำเข้าตาราง: {}", warning));
        } catch (RuntimeException e) {
            // นำเข้าพังไม่ควรทำให้ API ขึ้นไม่ได้ ข้อมูลเดิมในฐานยังตอบคำถามได้อยู่
            log.error("นำเข้าตารางไม่สำเร็จ: {}", e.getMessage(), e);
        }
    }
}
