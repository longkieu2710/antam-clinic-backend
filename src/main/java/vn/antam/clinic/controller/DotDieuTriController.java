package vn.antam.clinic.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.NgungDieuTriRequest;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.service.LuotChuaService;

/** Đợt điều trị (CHAN_DOAN_DIEU_TRI) tại chi nhánh đang chọn. */
@RestController
public class DotDieuTriController {

    private final LuotChuaService service;

    public DotDieuTriController(LuotChuaService service) {
        this.service = service;
    }

    /** Các đợt "Đang điều trị" của bệnh nhân — dùng ở màn Tiếp đón và Tạo lượt chữa. */
    @GetMapping("/api/benh-nhan/{maBN}/dot-dang-dieu-tri")
    public List<DotDieuTriResponse> dangDieuTri(@PathVariable String maBN) {
        return service.dotDangDieuTri(maBN);
    }

    /** Đánh dấu đợt "Ngừng điều trị" (bệnh nhân bỏ dở — BR12). Body có thể bỏ trống. */
    @PutMapping("/api/dot-dieu-tri/{maCDDT}/ngung")
    public DotDieuTriResponse ngung(@PathVariable String maCDDT,
                                    @RequestBody(required = false) NgungDieuTriRequest request) {
        return service.ngungDieuTri(maCDDT, request);
    }
}
