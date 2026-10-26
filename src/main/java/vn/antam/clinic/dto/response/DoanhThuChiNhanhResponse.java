package vn.antam.clinic.dto.response;

import java.math.BigDecimal;

/** Doanh thu cả kỳ của một chi nhánh. @param tyTrong phần trăm trên doanh thu toàn chuỗi (0–100) */
public record DoanhThuChiNhanhResponse(
        String maCn,
        String tenCn,
        long soLuot,
        BigDecimal tienKham,
        BigDecimal tienDichVu,
        BigDecimal tienThuoc,
        BigDecimal tienThietBi,
        BigDecimal tong,
        double tyTrong) {
}
