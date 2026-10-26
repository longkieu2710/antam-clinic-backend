package vn.antam.clinic.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.response.DoanhThuResponse;
import vn.antam.clinic.dto.response.DoanhThuThangResponse;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.DoanhThuRepository;

/** F5 — doanh thu của chi nhánh đang chọn theo kỳ. */
@Service
public class DoanhThuService {

    private final DoanhThuRepository repo;

    public DoanhThuService(DoanhThuRepository repo) {
        this.repo = repo;
    }

    /**
     * @param tu  ngày đầu kỳ (tính cả ngày này); null = ngày đầu tháng hiện tại
     * @param den ngày cuối kỳ (tính cả ngày này); null = ngày cuối tháng của {@code tu}
     */
    @Transactional(readOnly = true)
    public DoanhThuResponse doanhThu(LocalDate tu, LocalDate den) {
        LocalDate from = tu != null ? tu : YearMonth.now().atDay(1);
        LocalDate to = den != null ? den : YearMonth.from(from).atEndOfMonth();
        if (to.isBefore(from)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_RANGE", "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();
        List<DoanhThuThangResponse> thang = repo.theoThang(start, end);
        long soLuot = thang.stream().mapToLong(DoanhThuThangResponse::soLuot).sum();
        BigDecimal kham = sum(thang, DoanhThuThangResponse::tienKham);
        BigDecimal dichVu = sum(thang, DoanhThuThangResponse::tienDichVu);
        BigDecimal thuoc = sum(thang, DoanhThuThangResponse::tienThuoc);
        BigDecimal thietBi = sum(thang, DoanhThuThangResponse::tienThietBi);
        return new DoanhThuResponse(from, to, soLuot, kham, dichVu, thuoc, thietBi,
                kham.add(dichVu).add(thuoc).add(thietBi), thang, repo.chiTiet(start, end));
    }

    private static BigDecimal sum(List<DoanhThuThangResponse> rows, Function<DoanhThuThangResponse, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
