package vn.antam.clinic.dto.response;

import java.math.BigDecimal;

/**
 * Doanh thu một tháng trong kỳ báo cáo (tính theo thời gian của lượt khám/chữa).
 *
 * @param thang      dạng yyyy-MM
 * @param tienKham   dịch vụ có LoaiDV "Khám bệnh" (lần chữa miễn phí khám — BR11)
 * @param tienDichVu các dịch vụ y tế khác (xét nghiệm, chẩn đoán hình ảnh…)
 */
public record DoanhThuThangResponse(
        String thang,
        long soLuot,
        BigDecimal tienKham,
        BigDecimal tienDichVu,
        BigDecimal tienThuoc,
        BigDecimal tienThietBi,
        BigDecimal tong) {
}
