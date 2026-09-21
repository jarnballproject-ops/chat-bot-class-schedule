package com.timetable.api.common;

import org.springframework.http.HttpStatus;

/**
 * ข้อผิดพลาดเชิงธุรกิจที่รู้สาเหตุแน่ชัด GlobalExceptionHandler จะแปลงเป็น problem+json
 * slug กลายเป็น type URI เช่น "alias-conflict" -> https://timetable.local/errors/alias-conflict
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String slug;

    public ApiException(HttpStatus status, String slug, String detail) {
        super(detail);
        this.status = status;
        this.slug = slug;
    }

    public static ApiException notFound(String slug, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, slug, detail);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getSlug() {
        return slug;
    }
}
