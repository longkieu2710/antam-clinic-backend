package vn.antam.clinic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.response.TongQuanResponse;
import vn.antam.clinic.service.TongQuanService;

/** Tổng quan chi nhánh đang chọn (X-Site). */
@RestController
public class TongQuanController {

    private final TongQuanService service;

    public TongQuanController(TongQuanService service) {
        this.service = service;
    }

    @GetMapping("/api/tong-quan")
    public TongQuanResponse tongQuan() {
        return service.tongQuan();
    }
}
