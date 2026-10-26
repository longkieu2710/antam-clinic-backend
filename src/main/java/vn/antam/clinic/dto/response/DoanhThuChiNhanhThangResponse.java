package vn.antam.clinic.dto.response;

import java.math.BigDecimal;

/** Doanh thu một chi nhánh trong một tháng (S0: LUOT_KHAM_CHUA.MaCN_PhatSinh). @param thang yyyy-MM */
public record DoanhThuChiNhanhThangResponse(
        String maCn,
        String thang,
        long soLuot,
        BigDecimal tienKham,
        BigDecimal tienDichVu,
        BigDecimal tienThuoc,
        BigDecimal tienThietBi,
        BigDecimal tong) {
}
