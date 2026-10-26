package vn.antam.clinic.entity;

import java.util.List;

/**
 * Một dòng bảng LAN_CHUA (chuyên biệt hoá từ LUOT_KHAM_CHUA, cùng khoá MaLuot).
 * Mỗi (MaCDDT, LanThu) chỉ có một dòng.
 *
 * @param maCDDT đợt điều trị mà lần chữa này thuộc về
 * @param lanThu thứ tự lần chữa trong đợt, bắt đầu từ 1
 */
public record LanChua(
        String maLuot,
        String maCDDT,
        Integer lanThu,
        String ketLuanSauChua,
        String tinhTrangSauChua) {

    /** Tình trạng sau chữa có thể chọn; "Khỏi" kết thúc đợt điều trị (BR10). */
    public static final String KHOI = "Khỏi";
    public static final List<String> TINH_TRANG = List.of("Đỡ", "Không đổi", "Nặng hơn", KHOI);
}
