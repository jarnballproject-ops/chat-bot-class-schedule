package com.timetable.api.teacher;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class TeacherImportService {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final TeacherRepository repository;

    public TeacherImportService(TeacherRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file) throws IOException {
        List<ImportResult.Rejected> rejected = new ArrayList<>();
        int inserted = 0;
        int updated = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .get();

        try (Reader reader = new InputStreamReader(stripBom(file.getBytes()), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {

            for (CSVRecord row : parser) {
                try {
                    String code = required(row, "code");
                    String firstName = required(row, "first_name");
                    String lastName = required(row, "last_name");

                    Teacher teacher = repository.findByCode(code).orElse(null);
                    boolean isNew = teacher == null;
                    if (isNew) {
                        teacher = new Teacher();
                        teacher.setCode(code);
                    }
                    teacher.setPrefix(optional(row, "prefix"));
                    teacher.setFirstName(firstName);
                    teacher.setLastName(lastName);
                    teacher.setDepartment(optional(row, "department"));
                    repository.save(teacher);

                    if (isNew) {
                        inserted++;
                    } else {
                        updated++;
                    }
                } catch (RuntimeException ex) {
                    // แถวเดียวเสียไม่ควรทำให้ทั้งไฟล์ตก เก็บเหตุผลไว้แล้วไปแถวถัดไป
                    rejected.add(new ImportResult.Rejected(row.getRecordNumber() + 1, ex.getMessage()));
                }
            }
        }

        return new ImportResult(inserted, updated, rejected);
    }

    private static ByteArrayInputStream stripBom(byte[] bytes) {
        int offset = startsWithBom(bytes) ? UTF8_BOM.length : 0;
        return new ByteArrayInputStream(bytes, offset, bytes.length - offset);
    }

    private static boolean startsWithBom(byte[] bytes) {
        if (bytes.length < UTF8_BOM.length) {
            return false;
        }
        for (int i = 0; i < UTF8_BOM.length; i++) {
            if (bytes[i] != UTF8_BOM[i]) {
                return false;
            }
        }
        return true;
    }

    private static String required(CSVRecord row, String column) {
        String value = optional(row, column);
        if (value == null) {
            throw new IllegalArgumentException("คอลัมน์ " + column + " ว่างหรือไม่มี");
        }
        return value;
    }

    private static String optional(CSVRecord row, String column) {
        if (!row.isMapped(column)) {
            return null;
        }
        String value = row.get(column);
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
