package vn.antam.clinic.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.response.SiteStatusResponse;
import vn.antam.clinic.service.SiteHealthService;

/**
 * Kiểm tra kết nối tới cả 4 trạm (không đi qua X-Site).
 * Màn "Tổng quan" của frontend dùng API này để hiện trạng thái cụm CSDL phân tán.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SiteHealthService service;

    public SystemController(SiteHealthService service) {
        this.service = service;
    }

    @GetMapping("/sites")
    public List<SiteStatusResponse> sites() {
        return service.checkAll();
    }
}
