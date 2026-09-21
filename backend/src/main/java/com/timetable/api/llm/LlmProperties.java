package com.timetable.api.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ตั้งค่า sidecar โมเดลภาษา (OpenAI-compatible)
 * ค่าเริ่มต้น enabled=false เพื่อให้ระบบยังตอบได้จาก SQL แม้ยังไม่ได้เปิด sidecar (ADR-10 fallback)
 *
 * <p>รองรับ LoRA สองทาง ขึ้นกับว่า sidecar เป็นตัวไหน
 * <ul>
 *   <li><b>llama.cpp server</b> — โหลด adapter ด้วย {@code --lora} ตอนสตาร์ท แล้วสั่งเปิด/ปิดรายคำขอ
 *       ผ่านฟิลด์ {@code lora} ใน body ซึ่งมาจาก {@code lora-id} + {@code lora-scale} ตรงนี้
 *       (scale = 0 คือไม่ส่งฟิลด์นั้นเลย = ใช้โมเดลฐานล้วน)</li>
 *   <li><b>Ollama</b> — merge adapter เข้ากับโมเดลฐานแล้ว publish เป็นโมเดลชื่อใหม่
 *       กรณีนี้เปลี่ยนแค่ {@code model} ไม่ต้องยุ่งกับ lora-*</li>
 * </ul>
 */
@ConfigurationProperties("app.llm")
public record LlmProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxTokens,
        int connectTimeoutMs,
        int readTimeoutMs,
        int loraId,
        double loraScale) {

    public LlmProperties {
        baseUrl = baseUrl == null ? "http://localhost:8102/v1" : baseUrl;
        model = model == null ? "qwen2.5-1.5b-instruct" : model;
        maxTokens = maxTokens <= 0 ? 320 : maxTokens;
        connectTimeoutMs = connectTimeoutMs <= 0 ? 2000 : connectTimeoutMs;
        readTimeoutMs = readTimeoutMs <= 0 ? 30000 : readTimeoutMs;
    }

    /** ชื่อรุ่นที่ใช้ตอบ ใส่ท้ายด้วยสเกล LoRA เพื่อให้ย้อนรอยคำตอบได้ว่ามาจาก adapter หรือโมเดลฐาน */
    public String version() {
        return loraScale > 0 ? model + "+lora" + loraId + "@" + loraScale : model;
    }
}
