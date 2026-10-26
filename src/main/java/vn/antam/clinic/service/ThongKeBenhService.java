package vn.antam.clinic.service;

import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.response.ThongKeBenhItemResponse;
import vn.antam.clinic.dto.response.ThongKeBenhResponse;
import vn.antam.clinic.repository.ThongKeBenhRepository;

/** F2 — thống kê bệnh mắc phải theo tháng tại chi nhánh đang chọn. */
@Service
public class ThongKeBenhService {

    private final ThongKeBenhRepository repo;

    public ThongKeBenhService(ThongKeBenhRepository repo) {
        this.repo = repo;
    }

    /** @param thang null = tháng hiện tại */
    @Transactional(readOnly = true)
    public ThongKeBenhResponse thongKe(YearMonth thang) {
        YearMonth month = thang != null ? thang : YearMonth.now();
        List<ThongKeBenhItemResponse> rows = repo.thongKe(month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
        long tong = rows.stream().mapToLong(ThongKeBenhItemResponse::soLanMac).sum();
        return new ThongKeBenhResponse(month.toString(), tong, rows);
    }
}
