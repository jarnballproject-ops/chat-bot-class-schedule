package com.timetable.api.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ตรวจคำตอบของโมเดลเทียบกับตารางจริงก่อนส่งออก (docs/แผนงาน.md ข้อ 7 กฎข้อ 2)
 * ไม่ผ่าน = ทิ้งคำตอบโมเดล ใช้คำตอบจาก SQL แทน และนับเข้า fallbackRate
 *
 * <p>ตรวจสามอย่างที่ผิดแล้วคนเชื่อทันที: เวลา รหัสวิชา และกลุ่มเรียน
 * ponytail: ยังไม่ตรวจจำนวนคาบกับชื่อห้อง เพราะจำนวนเป็นค่าที่คำนวณ (ไม่มีใน JSON ตรง ๆ)
 * และชื่อห้องเป็นข้อความอิสระที่สะกดต่างกันได้ ถ้าเจอเคสที่โมเดลมั่วสองอย่างนี้จริงค่อยเพิ่ม
 */
public final class AnswerVerifier {

    /** เวลาในคำตอบ เช่น 10:00 — ตารางอาชีวะเริ่มคาบที่ชั่วโมงเต็มเสมอ */
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

    /** รหัสวิชาอาชีวะ เช่น 31900-1003 */
    private static final Pattern SUBJECT_CODE = Pattern.compile("\\d{5}-\\d{4}");

    /** รหัสกลุ่มเรียน เช่น สท.4/1 หรือ คภ.5/1-2 */
    private static final Pattern GROUP = Pattern.compile("[^\\s,()·]*\\d+/\\d+(-\\d+)?");

    private AnswerVerifier() {
    }

    /** คืนรายการข้อความที่ตรวจไม่ผ่าน ว่างเปล่า = คำตอบใช้ได้ */
    public static List<String> verify(String answer, ScheduleContext context) {
        List<String> mismatched = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            mismatched.add("คำตอบว่าง");
            return mismatched;
        }

        Matcher time = TIME.matcher(answer);
        while (time.find()) {
            int hour = Integer.parseInt(time.group(1));
            boolean onTheHour = "00".equals(time.group(2));
            if (!onTheHour || !context.hours().contains(hour)) {
                mismatched.add("เวลา " + time.group());
            }
        }

        Matcher code = SUBJECT_CODE.matcher(answer);
        while (code.find()) {
            if (!context.codes().contains(code.group())) {
                mismatched.add("รหัสวิชา " + code.group());
            }
        }

        Matcher group = GROUP.matcher(answer);
        while (group.find()) {
            String token = trimTail(group.group());
            // ข้ามสิ่งที่เป็นเวลาหรือรหัสวิชาซึ่งตรวจไปแล้วข้างบน
            if (token.contains(":") || SUBJECT_CODE.matcher(token).find()) {
                continue;
            }
            if (!context.groups().contains(token)) {
                mismatched.add("กลุ่มเรียน " + token);
            }
        }
        return mismatched;
    }

    /** ตัดเครื่องหมายวรรคตอนท้ายคำที่ติดมากับ token เช่น "สท.4/1," หรือ "คภ.5/1-2." */
    private static String trimTail(String token) {
        int end = token.length();
        while (end > 0 && ".,;:()".indexOf(token.charAt(end - 1)) >= 0) {
            end--;
        }
        return token.substring(0, end);
    }
}
