package vn.antam.clinic.dto.response;

import java.math.BigDecimal;

import vn.antam.clinic.entity.DichVu;

/**
 * Dịch vụ y tế trả về FE.
 *
 * @param soLanSuDung số dòng SU_DUNG_DV dùng dịch vụ này trên toàn chuỗi (view toàn cục tại S0);
 *                    null nếu không đọc được (một chi nhánh đang tắt) hoặc không cần (bản sao ở chi nhánh)
 */
public record DichVuResponse(String maDV, String tenDV, String loaiDV, String donViTinh, BigDecimal donGia,
                             Long soLanSuDung) {

    public static DichVuResponse from(DichVu dv, Long soLanSuDung) {
        return new DichVuResponse(dv.maDV(), dv.tenDV(), dv.loaiDV(), dv.donViTinh(), dv.donGia(), soLanSuDung);
    }
}
