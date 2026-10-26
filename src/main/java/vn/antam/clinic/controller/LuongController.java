package vn.antam.clinic.controller;

import java.time.YearMonth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.response.BangLuongResponse;
import vn.antam.clinic.service.LuongService;

/** F3 — bảng lương bác sĩ, y tá của chi nhánh đang chọn (header X-Site). */
@RestController
public class LuongController {

    private final LuongService service;

    public LuongController(LuongService service) {
        this.service = service;
    }

    /** @param thang dạng yyyy-MM, bỏ trống = tháng hiện tại */
    @GetMapping("/api/bao-cao/luong")
    public BangLuongResponse bangLuong(@RequestParam(required = false) YearMonth thang) {
        return service.bangLuong(thang);
    }
}
