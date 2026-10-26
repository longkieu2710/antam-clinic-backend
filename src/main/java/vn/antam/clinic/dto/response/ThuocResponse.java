package vn.antam.clinic.dto.response;

import java.math.BigDecimal;

import vn.antam.clinic.entity.Thuoc;

/**
 * Thuốc trả về FE.
 *
 * @param soLanKe số dòng KE_DON dùng thuốc này trên toàn chuỗi (view toàn cục tại S0);
 *                null nếu không đọc được (một chi nhánh đang tắt) hoặc không cần (bản sao ở chi nhánh)
 */
public record ThuocResponse(String maThuoc, String tenThuoc, String hoatChat, String donViTinh, BigDecimal donGia,
                            String hangSX, Long soLanKe) {

    public static ThuocResponse from(Thuoc thuoc, Long soLanKe) {
        return new ThuocResponse(thuoc.maThuoc(), thuoc.tenThuoc(), thuoc.hoatChat(), thuoc.donViTinh(),
                thuoc.donGia(), thuoc.hangSX(), soLanKe);
    }
}
