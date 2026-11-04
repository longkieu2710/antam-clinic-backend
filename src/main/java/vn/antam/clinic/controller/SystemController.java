package vn.antam.clinic.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.response.AppModeResponse;
import vn.antam.clinic.dto.response.SiteStatusResponse;
import vn.antam.clinic.service.AppModeService;
import vn.antam.clinic.service.SiteHealthService;

/**
 * Kiểm tra kết nối tới các trạm và cho biết bản triển khai đang chạy (không đi qua X-Site).
 * Màn "Tổng quan" của frontend dùng /sites để hiện trạng thái cụm CSDL phân tán; /mode để chọn giao diện.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SiteHealthService service;
    private final AppModeService modeService;

    public SystemController(SiteHealthService service, AppModeService modeService) {
        this.service = service;
        this.modeService = modeService;
    }

    @GetMapping("/sites")
    public List<SiteStatusResponse> sites() {
        return service.checkAll();
    }

    @GetMapping("/mode")
    public AppModeResponse mode() {
        return modeService.mode();
    }
}
