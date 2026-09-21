package com.timetable.api.teacher;

import com.timetable.api.auth.RequireRole;
import com.timetable.api.common.ApiException;
import com.timetable.api.schedule.TeacherScheduleRow;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private final TeacherRepository repository;
    private final TeacherImportService importService;

    public TeacherController(TeacherRepository repository, TeacherImportService importService) {
        this.repository = repository;
        this.importService = importService;
    }

    @GetMapping
    public List<Teacher> list() {
        return repository.findAll();
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole("staff")
    public ImportResult importTeachers(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "empty-file", "ไฟล์ที่อัปโหลดว่างเปล่า");
        }
        return importService.importCsv(file);
    }

    @GetMapping("/{id}/schedule")
    public List<TeacherScheduleRow> schedule(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound("teacher-not-found", "ไม่พบอาจารย์ id=" + id);
        }
        return repository.findScheduleByTeacherId(id);
    }
}
