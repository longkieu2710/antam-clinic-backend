package vn.antam.clinic.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.response.BenhNhanTomTatResponse;
import vn.antam.clinic.dto.response.DoanhThuToanChuoiResponse;
import vn.antam.clinic.dto.response.HoSoBenhNhanResponse;
import vn.antam.clinic.dto.response.ThongKeBenhToanChuoiResponse;
import vn.antam.clinic.service.ToanChuoiService;

/** Báo cáo toàn chuỗi — /api/hq/** luôn chạy ở trạm trung tâm S0 (view toàn cục qua postgres_fdw). */
@RestController
@RequestMapping("/api/hq")
public class ToanChuoiController {

    private final ToanChuoiService service;

    public ToanChuoiController(ToanChuoiService service) {
        this.service = service;
    }

    @GetMapping("/benh-nhan")
    public List<BenhNhanTomTatResponse> benhNhan(@RequestParam(required = false) String q) {
        return service.benhNhan(q);
    }

    /** @param ma danh sách mã bệnh nhân, ngăn cách bởi dấu phẩy → {maBN: [CN1, CN3]} */
    @GetMapping("/benh-nhan/chi-nhanh")
    public Map<String, List<String>> chiNhanh(@RequestParam List<String> ma) {
        return service.chiNhanhCoHoSo(ma);
    }

    @GetMapping("/benh-nhan/{maBN}/ho-so")
    public HoSoBenhNhanResponse hoSo(@PathVariable String maBN) {
        return service.hoSo(maBN);
    }

    @GetMapping("/doanh-thu")
    public DoanhThuToanChuoiResponse doanhThu(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tu,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate den) {
        return service.doanhThu(tu, den);
    }

    /** @param thang yyyy-MM, bỏ trống = tháng hiện tại */
    @GetMapping("/thong-ke-benh")
    public ThongKeBenhToanChuoiResponse thongKeBenh(@RequestParam(required = false) YearMonth thang) {
        return service.thongKeBenh(thang);
    }
}
