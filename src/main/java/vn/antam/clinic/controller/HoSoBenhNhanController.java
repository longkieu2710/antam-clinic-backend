package vn.antam.clinic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.response.HoSoBenhNhanResponse;
import vn.antam.clinic.service.HoSoBenhNhanService;

/** F4 — hồ sơ bệnh nhân của chi nhánh đang chọn (header X-Site). */
@RestController
public class HoSoBenhNhanController {

    private final HoSoBenhNhanService service;

    public HoSoBenhNhanController(HoSoBenhNhanService service) {
        this.service = service;
    }

    @GetMapping("/api/benh-nhan/{maBN}/ho-so")
    public HoSoBenhNhanResponse hoSo(@PathVariable String maBN) {
        return service.hoSo(maBN);
    }
}
