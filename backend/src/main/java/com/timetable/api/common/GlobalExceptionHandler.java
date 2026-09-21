package com.timetable.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * error ของ Spring เอง (400/404/405/415 ฯลฯ) ออกเป็น problem+json อยู่แล้วผ่าน
 * spring.mvc.problemdetails.enabled ที่นี่จึงดักเฉพาะของที่ Spring ไม่รู้จัก
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex, HttpServletRequest request) {
        return ProblemSupport.of(ex.getStatus(), ex.getSlug(), ex.getStatus().getReasonPhrase(),
                ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        // log ของจริงไว้ฝั่งเรา แต่ไม่ส่งรายละเอียดภายในออกไปให้ client
        log.error("unhandled exception on {}", request.getRequestURI(), ex);
        return ProblemSupport.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "เกิดข้อผิดพลาดภายในระบบ",
                "ระบบทำงานผิดพลาด กรุณาลองใหม่", request.getRequestURI());
    }
}
