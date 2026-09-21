package com.timetable.api.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;

/**
 * คาบเรียนหนึ่งแถวในตาราง session
 * conf / bbox / source_doc_id ไม่ map ไว้เพราะหน้าแก้ตารางไม่แตะค่าพวกนั้น
 * (Flyway เป็นเจ้าของ schema อยู่แล้ว Hibernate จึงไม่ต้องรู้จักทุกคอลัมน์)
 */
@Entity
@Table(name = "session")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String semester;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private String kind;

    @Column(name = "subject_code")
    private String subjectCode;

    @Column(name = "room_code")
    private String roomCode;

    @Column(name = "group_code")
    private String groupCode;

    @Column(nullable = false)
    private String status;

    public Long getId() {
        return id;
    }

    public String getSemester() {
        return semester;
    }

    public short getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(short dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getStatus() {
        return status;
    }
}
