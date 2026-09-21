package com.timetable.api.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** ติดบน handler method ที่ต้องการบทบาทเฉพาะ ไม่ติด = ทุกบทบาทที่ยืนยันตัวแล้วเข้าได้ */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String[] value();
}
