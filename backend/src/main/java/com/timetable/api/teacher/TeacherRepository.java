package com.timetable.api.teacher;

import com.timetable.api.schedule.TeacherScheduleRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByCode(String code);

    // alias ใส่ " " เพื่อกัน Postgres พับชื่อคอลัมน์เป็นตัวเล็ก ไม่งั้น projection จับคู่ไม่ติด
    @Query(value = """
            select session_id   as "sessionId",
                   semester     as "semester",
                   day_of_week  as "dayOfWeek",
                   start_time   as "startTime",
                   end_time     as "endTime",
                   kind         as "kind",
                   subject_code as "subjectCode",
                   subject_name as "subjectName",
                   room_code    as "roomCode",
                   group_code   as "groupCode",
                   headcount    as "headcount"
            from v_teacher_schedule
            where teacher_id = :teacherId
            order by day_of_week, start_time
            """, nativeQuery = true)
    List<TeacherScheduleRow> findScheduleByTeacherId(@Param("teacherId") Long teacherId);
}
