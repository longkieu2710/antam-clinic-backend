package vn.antam.clinic.entity;

import java.math.BigDecimal;

/**
 * Một dòng bảng SU_DUNG_DV — dịch vụ dùng trong một lượt khám/chữa. Khoá chính (MaLuot, MaDV).
 *
 * @param donGiaApDung đơn giá tại thời điểm dùng (đổi giá ở DICH_VU không ảnh hưởng lượt cũ)
 */
public record SuDungDv(
        String maLuot,
        String maDV,
        Integer soLuong,
        BigDecimal donGiaApDung,
        String ketQua) {
}
