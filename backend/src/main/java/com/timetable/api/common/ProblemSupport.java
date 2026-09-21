package com.timetable.api.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

/** สร้าง ProblemDetail ให้หน้าตาเหมือนกันทุกที่ ทั้งจาก controller advice และจาก servlet filter */
public final class ProblemSupport {

    public static final String TYPE_PREFIX = "https://timetable.local/errors/";

    private ProblemSupport() {
    }

    public static ProblemDetail of(HttpStatus status, String slug, String title, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + slug));
        problem.setTitle(title);
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        return problem;
    }
}
