package com.timetable.api.auth;

import tools.jackson.databind.ObjectMapper;
import com.timetable.api.common.ProblemSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ตรวจ Authorization: Bearer <token> เทียบกับ map ใน config
 *
 * ของชั่วคราวสำหรับ dev และการสาธิต ไม่ใช่ระบบ auth จริง เปลี่ยนเป็น OIDC ภายหลังโดย
 * ลบคลาสนี้แล้วใส่ spring-boot-starter-oauth2-resource-server สัญญา 401/403 ไม่เปลี่ยน
 *
 * fail closed: token ที่ไม่อยู่ใน map ถูกปฏิเสธเสมอ ไม่มีเส้นทางไหนที่ "ไม่มี config แล้วผ่าน"
 */
@Component
public class BearerAuthFilter extends OncePerRequestFilter {

    public static final String ROLE_ATTRIBUTE = "app.role";

    private static final String BEARER = "Bearer ";

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;

    public BearerAuthFilter(AuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            writeProblem(request, response, "missing-token", "ต้องแนบ Authorization: Bearer <token>");
            return;
        }

        String role = properties.getTokens().get(header.substring(BEARER.length()).trim());
        if (role == null) {
            writeProblem(request, response, "invalid-token", "token ไม่ถูกต้องหรือหมดอายุ");
            return;
        }

        request.setAttribute(ROLE_ATTRIBUTE, role);
        chain.doFilter(request, response);
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, String slug, String detail)
            throws IOException {
        ProblemDetail problem = ProblemSupport.of(HttpStatus.UNAUTHORIZED, slug, "ไม่ได้รับอนุญาต", detail,
                request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
