package com.timetable.api.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * คุยกับ sidecar โมเดลผ่าน /v1/chat/completions (สัญญาเดียวกับ llama.cpp server, Ollama, vLLM)
 * ยิงพลาดเมื่อไรคืน empty เสมอ ไม่โยน exception ออกไป เพราะชั้นแชทต้อง fallback ไป SQL ให้ได้
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final LlmProperties properties;
    private final RestClient rest;

    public LlmClient(LlmProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory);
        // sidecar ในเครื่องไม่ต้องใช้คีย์ ส่วน API นอก (opentyphoon.ai, OpenAI ฯลฯ) ต้องมี Bearer
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.apiKey());
        }
        this.rest = builder.build();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public String version() {
        return properties.version();
    }

    /** คืนข้อความที่โมเดลตอบ หรือ empty ถ้า sidecar ล่ม ช้าเกิน หรือตอบผิดรูป */
    public Optional<String> complete(String system, String user) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        // temperature 0 = คำตอบเดิมทุกครั้งสำหรับคำถามเดิม จำเป็นกับการตรวจคำตอบและการสาธิต
        body.put("temperature", properties.temperature());
        body.put("max_tokens", properties.maxTokens());
        if (properties.loraScale() > 0) {
            body.put("lora", List.of(Map.of("id", properties.loraId(), "scale", properties.loraScale())));
        }

        try {
            Map<?, ?> response = rest.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            // เดินลง choices[0].message.content ตรง ๆ แทนการ map เป็นคลาส เพราะ sidecar แต่ละตัว
            // ใส่ฟิลด์เสริมไม่เหมือนกัน (usage, timings, slot_id) แต่สามชั้นนี้เหมือนกันหมด
            List<?> choices = (List<?>) response.get("choices");
            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.getFirst()).get("message");
            Object text = message.get("content");
            return text instanceof String answer && !answer.isBlank()
                    ? Optional.of(answer.trim())
                    : Optional.empty();
        } catch (Exception e) {
            log.warn("เรียกโมเดลไม่สำเร็จ ใช้คำตอบจาก SQL แทน: {}", e.toString());
            return Optional.empty();
        }
    }
}
