package vn.antam.clinic.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.response.DoanhThuResponse;
import vn.antam.clinic.service.DoanhThuService;

/** F5 — doanh thu của chi nhánh đang chọn (header X-Site). */
@RestController
public class DoanhThuController {

    private final DoanhThuService service;

    public DoanhThuController(DoanhThuService service) {
        this.service = service;
    }

    /**
     * @param tu  yyyy-MM-dd, bỏ trống = đầu tháng hiện tại
     * @param den yyyy-MM-dd (tính cả ngày này), bỏ trống = cuối tháng của {@code tu}
     */
    @GetMapping("/api/bao-cao/doanh-thu")
    public DoanhThuResponse doanhThu(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tu,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate den) {
        return service.doanhThu(tu, den);
    }
}
