package vn.antam.clinic.dto.response;

import java.math.BigDecimal;

/**
 * Một khoản chi phí trong một lượt khám/chữa.
 *
 * @param loai    DICH_VU (SU_DUNG_DV) | THUOC (KE_DON) | THIET_BI (SU_DUNG_TB)
 * @param ghiChu  loại dịch vụ / liều dùng / loại thiết bị
 * @param donGia  đơn giá áp dụng tại thời điểm dùng (BR7)
 */
public record ChiPhiResponse(
        String maLuot,
        String loai,
        String ma,
        String ten,
        String ghiChu,
        Integer soLuong,
        BigDecimal donGia,
        BigDecimal thanhTien) {
}
