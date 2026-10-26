package vn.antam.clinic.controller;

import java.time.YearMonth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.response.ThongKeBenhResponse;
import vn.antam.clinic.service.ThongKeBenhService;

/** F2 — thống kê bệnh theo tháng của chi nhánh đang chọn (header X-Site). */
@RestController
public class ThongKeBenhController {

    private final ThongKeBenhService service;

    public ThongKeBenhController(ThongKeBenhService service) {
        this.service = service;
    }

    /** @param thang dạng yyyy-MM, bỏ trống = tháng hiện tại */
    @GetMapping("/api/bao-cao/thong-ke-benh")
    public ThongKeBenhResponse thongKe(@RequestParam(required = false) YearMonth thang) {
        return service.thongKe(thang);
    }
}
