package com.timetable.api.config;

import com.timetable.api.auth.RoleInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;

    /**
     * ว่างไว้เป็นค่าเริ่มต้น = ไม่เปิด CORS ให้ใคร โปรไฟล์ dev เท่านั้นที่ใส่ origin ของ Vite เข้ามา
     * prod เสิร์ฟ frontend จาก origin เดียวกันอยู่แล้วจึงไม่ต้องใช้
     */
    @Value("${app.cors.allowed-origins:}")
    private String[] allowedOrigins;

    public WebConfig(RoleInterceptor roleInterceptor) {
        this.roleInterceptor = roleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE")
                .allowedHeaders("Authorization", "Content-Type");
    }
}
