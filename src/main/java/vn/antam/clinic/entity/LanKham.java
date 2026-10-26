package vn.antam.clinic.entity;

/** Một dòng bảng LAN_KHAM (chuyên biệt hoá từ LUOT_KHAM_CHUA, cùng khoá MaLuot). */
public record LanKham(
        String maLuot,
        String maBN,
        String lyDoKham,
        String trieuChung,
        String ketLuanKham) {
}
