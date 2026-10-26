package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * F5 — doanh thu của chi nhánh trong kỳ [tu, den] (tính cả hai ngày): tiền khám, dịch vụ y tế, thuốc, thiết bị.
 * Mọi khoản tính theo đơn giá áp dụng lưu tại thời điểm dùng (BR7).
 */
public record DoanhThuResponse(
        LocalDate tu,
        LocalDate den,
        long soLuot,
        BigDecimal tienKham,
        BigDecimal tienDichVu,
        BigDecimal tienThuoc,
        BigDecimal tienThietBi,
        BigDecimal tongDoanhThu,
        List<DoanhThuThangResponse> theoThang,
        List<DoanhThuChiTietResponse> chiTiet) {
}
