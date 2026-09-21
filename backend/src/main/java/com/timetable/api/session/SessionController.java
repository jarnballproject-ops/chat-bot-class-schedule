package com.timetable.api.session;

import com.timetable.api.auth.BearerAuthFilter;
import com.timetable.api.auth.RequireRole;
import com.timetable.api.session.SessionService.PatchResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @PatchMapping("/{id}")
    @RequireRole({"staff", "teacher"})
    public PatchResult patch(@PathVariable Long id,
                             @RequestBody SessionPatch patch,
                             HttpServletRequest request) {
        // บทบาทมาจาก BearerAuthFilter ใช้เป็นผู้บันทึกใน field_correction
        // ยังไม่ใช่ตัวตนรายคนเพราะ SSO ยังไม่ต่อ (docs/แผนงาน.md ข้อ 15)
        Object role = request.getAttribute(BearerAuthFilter.ROLE_ATTRIBUTE);
        return service.patch(id, patch, role == null ? "unknown" : role.toString());
    }

    @DeleteMapping("/{id}")
    @RequireRole({"staff", "teacher"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
