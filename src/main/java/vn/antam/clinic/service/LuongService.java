package vn.antam.clinic.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.response.BangLuongResponse;
import vn.antam.clinic.dto.response.LuongNhanVienResponse;
import vn.antam.clinic.entity.LuongCoSo;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.LuongCoSoRepository;
import vn.antam.clinic.repository.LuongRepository;

/**
 * F3 — tính lương bác sĩ, y tá trong tháng hiện tại hoặc tháng chỉ định, tại chi nhánh đang chọn.
 * Lương cơ sở đọc từ bản sao LUONG_CO_SO ở chi nhánh (Logical Replication từ S0).
 */
@Service
public class LuongService {

    /** Thưởng mỗi đợt điều trị bác sĩ chữa khỏi. */
    public static final BigDecimal THUONG_MOI_DOT_KHOI = new BigDecimal("1000000");
    /** Thưởng mỗi lượt khám/chữa y tá tham gia hỗ trợ. */
    public static final BigDecimal THUONG_MOI_LAN_HO_TRO = new BigDecimal("200000");

    private final LuongRepository luongRepo;
    private final LuongCoSoRepository luongCoSoRepo;

    public LuongService(LuongRepository luongRepo, LuongCoSoRepository luongCoSoRepo) {
        this.luongRepo = luongRepo;
        this.luongCoSoRepo = luongCoSoRepo;
    }

    /**
     * @param thang null = tháng hiện tại (dữ liệu tính đến thời điểm hiện tại). Mức lương cơ sở là mức có ngày áp dụng
     *              gần nhất, không muộn hơn ngày cuối tháng.
     */
    @Transactional(readOnly = true)
    public BangLuongResponse bangLuong(YearMonth thang) {
        YearMonth month = thang != null ? thang : YearMonth.now();
        LuongCoSo coSo = luongCoSoRepo.findApDungTai(month.atEndOfMonth())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_BASE_SALARY",
                        "Chưa có mức lương cơ sở nào áp dụng cho tháng " + month + " — thêm ở màn Lương cơ sở (S0)."));
        LocalDateTime tu = month.atDay(1).atStartOfDay();
        LocalDateTime den = month.plusMonths(1).atDay(1).atStartOfDay();
        List<LuongNhanVienResponse> rows = luongRepo.tinhLuong(tu, den, coSo.mucLuong(), THUONG_MOI_DOT_KHOI,
                THUONG_MOI_LAN_HO_TRO);
        BigDecimal tong = rows.stream().map(LuongNhanVienResponse::tongLuong).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BangLuongResponse(month.toString(), coSo.ngayApDung(), coSo.mucLuong(), THUONG_MOI_DOT_KHOI,
                THUONG_MOI_LAN_HO_TRO, tong, rows);
    }
}
