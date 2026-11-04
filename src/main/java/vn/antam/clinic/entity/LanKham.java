package vn.antam.clinic.entity;

/** Một dòng bảng LAN_KHAM (chuyên biệt hoá từ LUOT_KHAM_CHUA, cùng khoá MaLuot). */
public record LanKham(
        String maLuot,
        String maBN,
        String lyDoKham,
        String trieuChung,
        String ketLuanKham) {

    /** Kết luận khi lượt khám không chẩn đoán bệnh nào và người dùng không nhập kết luận. */
    public static final String KHONG_PHAT_HIEN_BENH = "Không phát hiện bệnh";
}
