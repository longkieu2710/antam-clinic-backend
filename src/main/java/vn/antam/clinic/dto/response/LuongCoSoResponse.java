package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import vn.antam.clinic.entity.LuongCoSo;

/**
 * Một mức lương cơ sở.
 *
 * @param trangThai DANG_AP_DUNG (mức mới nhất có ngày áp dụng ≤ hôm nay), DA_THAY_THE (mức cũ hơn),
 *                  SAP_AP_DUNG (ngày áp dụng ở tương lai — chỉ mức này được sửa / xoá)
 */
public record LuongCoSoResponse(LocalDate ngayApDung, BigDecimal mucLuong, String vanBanQuyDinh, String trangThai) {

    public static final String DANG_AP_DUNG = "DANG_AP_DUNG";
    public static final String DA_THAY_THE = "DA_THAY_THE";
    public static final String SAP_AP_DUNG = "SAP_AP_DUNG";

    public static LuongCoSoResponse from(LuongCoSo row, String trangThai) {
        return new LuongCoSoResponse(row.ngayApDung(), row.mucLuong(), row.vanBanQuyDinh(), trangThai);
    }
}
