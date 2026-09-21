package com.timetable.api.doc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimetableDocRepository extends JpaRepository<TimetableDoc, Long> {

    Optional<TimetableDoc> findBySha256(String sha256);

    List<TimetableDoc> findAllByOrderByUploadedAtDesc();

    List<TimetableDoc> findByTeacherIdOrderByUploadedAtDesc(Long teacherId);
}
