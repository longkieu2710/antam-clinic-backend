package vn.antam.clinic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.response.BenhNhanTomTatResponse;
import vn.antam.clinic.dto.response.DoanhThuChiNhanhResponse;
import vn.antam.clinic.dto.response.DoanhThuChiNhanhThangResponse;
import vn.antam.clinic.dto.response.DoanhThuResponse;
import vn.antam.clinic.dto.response.DoanhThuToanChuoiResponse;
import vn.antam.clinic.dto.response.HoSoBenhNhanResponse;
import vn.antam.clinic.dto.response.ThongKeBenhChiNhanhResponse;
import vn.antam.clinic.dto.response.ThongKeBenhItemResponse;
import vn.antam.clinic.dto.response.ThongKeBenhResponse;
import vn.antam.clinic.dto.response.ThongKeBenhToanChuoiItemResponse;
import vn.antam.clinic.dto.response.ThongKeBenhToanChuoiResponse;
import vn.antam.clinic.entity.ChiNhanh;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.ChiNhanhRepository;
import vn.antam.clinic.repository.ToanChuoiRepository;
import vn.antam.clinic.util.Codes;

/**
 * Báo cáo toàn chuỗi (/api/hq/**, luôn chạy ở S0). S0 đọc 3 chi nhánh qua postgres_fdw nên một chi nhánh tắt là câu
 * lệnh lỗi — khi đó trả 503 nêu rõ trạm nào không phản hồi.
 * Các báo cáo dùng lại service F2 / F4 / F5: SQL của chúng chỉ dùng tên bảng, ở S0 là view toàn cục cùng tên.
 */
@Service
public class ToanChuoiService {

    private final ToanChuoiRepository repo;
    private final ChiNhanhRepository chiNhanhRepo;
    private final HoSoBenhNhanService hoSoService;
    private final DoanhThuService doanhThuService;
    private final ThongKeBenhService thongKeBenhService;
    private final SiteHealthService health;

    public ToanChuoiService(ToanChuoiRepository repo, ChiNhanhRepository chiNhanhRepo, HoSoBenhNhanService hoSoService,
                            DoanhThuService doanhThuService, ThongKeBenhService thongKeBenhService,
                            SiteHealthService health) {
        this.repo = repo;
        this.chiNhanhRepo = chiNhanhRepo;
        this.hoSoService = hoSoService;
        this.doanhThuService = doanhThuService;
        this.thongKeBenhService = thongKeBenhService;
        this.health = health;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<BenhNhanTomTatResponse> benhNhan(String q) {
        return chain(() -> repo.searchBenhNhan(Codes.blankToNull(q)));
    }

    /** @param maBN danh sách mã cần tra; tối đa 50 mã */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, List<String>> chiNhanhCoHoSo(List<String> maBN) {
        List<String> ma = maBN == null ? List.of() : maBN.stream().map(String::trim).filter(s -> !s.isEmpty())
                .distinct().limit(50).toList();
        return chain(() -> repo.chiNhanhCoHoSo(ma));
    }

    /** F4 toàn chuỗi: mỗi lượt / đợt mang mã chi nhánh phát sinh. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public HoSoBenhNhanResponse hoSo(String maBN) {
        return chain(() -> hoSoService.hoSo(maBN));
    }

    /** F5 toàn chuỗi: gộp + từng chi nhánh + từng chi nhánh × tháng. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DoanhThuToanChuoiResponse doanhThu(LocalDate tu, LocalDate den) {
        return chain(() -> {
            DoanhThuResponse tong = doanhThuService.doanhThu(tu, den);
            List<DoanhThuChiNhanhThangResponse> rows = repo.doanhThuTheoChiNhanh(tong.tu().atStartOfDay(),
                    tong.den().plusDays(1).atStartOfDay());
            List<DoanhThuChiNhanhResponse> chiNhanh = new ArrayList<>();
            for (ChiNhanh cn : chiNhanhRepo.findAll()) {
                List<DoanhThuChiNhanhThangResponse> mine = rows.stream().filter(x -> cn.maCn().equals(x.maCn())).toList();
                BigDecimal tongCn = sum(mine, DoanhThuChiNhanhThangResponse::tong);
                double tyTrong = tong.tongDoanhThu().signum() == 0 ? 0
                        : tongCn.multiply(BigDecimal.valueOf(100)).divide(tong.tongDoanhThu(), 1, RoundingMode.HALF_UP).doubleValue();
                chiNhanh.add(new DoanhThuChiNhanhResponse(cn.maCn(), cn.tenCn(),
                        mine.stream().mapToLong(DoanhThuChiNhanhThangResponse::soLuot).sum(),
                        sum(mine, DoanhThuChiNhanhThangResponse::tienKham),
                        sum(mine, DoanhThuChiNhanhThangResponse::tienDichVu),
                        sum(mine, DoanhThuChiNhanhThangResponse::tienThuoc),
                        sum(mine, DoanhThuChiNhanhThangResponse::tienThietBi),
                        tongCn, tyTrong));
            }
            return new DoanhThuToanChuoiResponse(tong, chiNhanh, rows);
        });
    }

    /** F2 toàn chuỗi: tổng mỗi bệnh trên cả chuỗi + số lần mắc tại từng chi nhánh. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ThongKeBenhToanChuoiResponse thongKeBenh(YearMonth thang) {
        return chain(() -> {
            ThongKeBenhResponse tong = thongKeBenhService.thongKe(thang);
            YearMonth month = YearMonth.parse(tong.thang());
            List<ThongKeBenhChiNhanhResponse> rows = repo.thongKeBenhTheoChiNhanh(month.atDay(1).atStartOfDay(),
                    month.plusMonths(1).atDay(1).atStartOfDay());
            List<String> chiNhanh = chiNhanhRepo.findAll().stream().map(ChiNhanh::maCn).toList();
            Map<String, Long> tongTheoCn = new LinkedHashMap<>();
            chiNhanh.forEach(cn -> tongTheoCn.put(cn, rows.stream().filter(x -> cn.equals(x.maCn()))
                    .mapToLong(ThongKeBenhChiNhanhResponse::soLanMac).sum()));
            Map<String, List<ThongKeBenhChiNhanhResponse>> byBenh = rows.stream()
                    .collect(Collectors.groupingBy(ThongKeBenhChiNhanhResponse::maBenh));
            List<ThongKeBenhToanChuoiItemResponse> benh = new ArrayList<>();
            for (ThongKeBenhItemResponse b : tong.benh()) {
                Map<String, Long> theoCn = new LinkedHashMap<>();
                chiNhanh.forEach(cn -> theoCn.put(cn, 0L));
                byBenh.getOrDefault(b.maBenh(), List.of()).forEach(x -> theoCn.put(x.maCn(), x.soLanMac()));
                benh.add(new ThongKeBenhToanChuoiItemResponse(b.maBenh(), b.tenBenh(), b.maICD10(), b.soLanMac(),
                        b.soBenhNhan(), theoCn));
            }
            return new ThongKeBenhToanChuoiResponse(tong.thang(), tong.tongLanMac(), chiNhanh, tongTheoCn, benh);
        });
    }

    /** Chạy truy vấn toàn chuỗi; lỗi CSDL → kiểm tra các trạm để báo đúng trạm đang tắt. */
    private <T> T chain(Supplier<T> query) {
        try {
            return query.get();
        } catch (DataAccessException | TransactionSystemException e) {
            List<String> down = health.checkAll().stream().filter(s -> !s.up())
                    .map(s -> s.site() + (s.maCn() != null ? " (" + s.maCn() + ")" : "")).toList();
            if (down.isEmpty()) {
                throw e;
            }
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CHAIN_UNAVAILABLE",
                    "Không lấy được dữ liệu toàn chuỗi: trạm " + String.join(", ", down)
                            + " không phản hồi. Báo cáo toàn chuỗi cần cả 4 trạm đang chạy.");
        }
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
