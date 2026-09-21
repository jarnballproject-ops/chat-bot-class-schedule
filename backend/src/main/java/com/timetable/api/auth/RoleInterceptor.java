package com.timetable.api.auth;

import tools.jackson.databind.ObjectMapper;
import com.timetable.api.common.ProblemSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/** บังคับ @RequireRole บน handler method โดยอ่าน role ที่ BearerAuthFilter ใส่ไว้ใน request */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public RoleInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireRole required = method.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            return true;
        }

        Object role = request.getAttribute(BearerAuthFilter.ROLE_ATTRIBUTE);
        if (role != null && Arrays.asList(required.value()).contains(role.toString())) {
            return true;
        }

        ProblemDetail problem = ProblemSupport.of(HttpStatus.FORBIDDEN, "insufficient-role", "สิทธิ์ไม่เพียงพอ",
                "ต้องเป็นบทบาท " + String.join(" หรือ ", required.value()), request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
        return false;
    }
}
