package com.timetable.api.chat;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ตารางของอาจารย์หนึ่งคนในรูปเดียวกับ dataset/schedule.json
 * รูปนี้คือรูปที่ dataset ตอนเทรน LoRA ใช้ ถ้าเปลี่ยนรูปตรงนี้ โมเดลที่เทรนมาจะอ่านไม่ออก
 *
 * @param json    ข้อความ JSON ที่ส่งให้โมเดลอ่าน
 * @param hours   ชั่วโมงเริ่ม/จบของทุกคาบ ใช้ตรวจว่าเวลาที่โมเดลพูดมีอยู่จริง
 * @param codes   รหัสวิชาทั้งหมดในตารางนี้
 * @param groups  รหัสกลุ่มเรียนทั้งหมดในตารางนี้
 * @param empty   ไม่มีคาบเลย (ไม่ต้องเรียกโมเดล)
 */
public record ScheduleContext(String json,
                              Set<Integer> hours,
                              Set<String> codes,
                              Set<String> groups,
                              boolean empty) {

    /** สร้างจากค่าที่ประกอบไว้แล้ว ใช้ในเทสต์ได้โดยไม่ต้องมีฐานข้อมูล */
    public static ScheduleContext of(String json, List<Integer> hours, List<String> codes, List<String> groups) {
        return new ScheduleContext(json, Set.copyOf(hours), Set.copyOf(codes), Set.copyOf(groups), hours.isEmpty());
    }

    /** โครงที่ ObjectMapper จะ serialize เป็น JSON — ชื่อคีย์ต้องตรงกับ dataset/schedule.json */
    public record Payload(String teacher,
                          String semester,
                          Map<String, Subject> subjects,
                          Map<String, Integer> groups,
                          Map<String, List<String>> combinedGroups,
                          List<Slot> slots) {
    }

    public record Subject(String name, Integer theory, Integer practice, Integer credits) {
    }

    public record Slot(String day, int start, int end, String code, String kind, String room, String group) {
    }
}
