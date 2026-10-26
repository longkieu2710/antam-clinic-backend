package vn.antam.clinic.entity;

import java.math.BigDecimal;

/**
 * Một dòng bảng SU_DUNG_TB — thiết bị dùng trong một lượt khám/chữa. Khoá chính (MaLuot, MaTB).
 *
 * @param donGiaApDung đơn giá sử dụng tại thời điểm dùng
 */
public record SuDungTb(
        String maLuot,
        String maTB,
        Integer soLan,
        BigDecimal donGiaApDung) {
}
