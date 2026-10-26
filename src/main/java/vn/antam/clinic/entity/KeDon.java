package vn.antam.clinic.entity;

import java.math.BigDecimal;

/**
 * Một dòng bảng KE_DON — thuốc kê trong một lượt khám/chữa. Khoá chính (MaLuot, MaThuoc).
 * Ở S0, KE_DON là view toàn cục gộp 3 chi nhánh qua postgres_fdw (cùng cột).
 *
 * @param donGiaApDung đơn giá tại thời điểm kê (BR7: đổi giá ở THUOC không ảnh hưởng đơn cũ)
 */
public record KeDon(
        String maLuot,
        String maThuoc,
        Integer soLuong,
        String lieuDung,
        BigDecimal donGiaApDung) {
}
