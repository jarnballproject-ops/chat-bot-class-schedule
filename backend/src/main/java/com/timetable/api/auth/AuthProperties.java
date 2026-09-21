package com.timetable.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * token ปลอมสำหรับ dev: key = token, value = role (student / staff / teacher)
 * ว่างไว้หมายถึงไม่มีใครเข้าได้ ซึ่งเป็นค่าเริ่มต้นของโปรไฟล์ prod
 */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private Map<String, String> tokens = new LinkedHashMap<>();

    public Map<String, String> getTokens() {
        return tokens;
    }

    public void setTokens(Map<String, String> tokens) {
        this.tokens = tokens;
    }
}
