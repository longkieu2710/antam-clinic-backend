package vn.antam.clinic.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Một dòng bảng THIET_BI — thiết bị y tế của một chi nhánh. */
public record ThietBi(
        String maTB,
        String tenTB,
        String loaiTB,
        BigDecimal donGiaSuDung,
        LocalDate ngayMua,
        String tinhTrang,
        String maCn) {
    public static final String HOAT_DONG_TOT = "Hoạt động tốt";
    /** Thiết bị đã dùng trong lượt khám/chữa thì không xoá mà chuyển trạng thái này (BR9). */
    public static final String NGUNG_HOAT_DONG = "Ngừng hoạt động";
}
