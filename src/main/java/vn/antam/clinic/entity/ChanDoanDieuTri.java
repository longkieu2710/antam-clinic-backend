package vn.antam.clinic.entity;

import java.time.LocalDateTime;

/**
 * Một dòng bảng CHAN_DOAN_DIEU_TRI — một bệnh được chẩn đoán trong một lần khám, mở ra đợt điều trị.
 * Mỗi (MaLuotKham, MaBenh) chỉ có một dòng.
 *
 * @param maLuotKham lần khám đưa ra chẩn đoán (LAN_KHAM.MaLuot)
 */
public record ChanDoanDieuTri(
        String maCDDT,
        String maLuotKham,
        String maBenh,
        String mucDo,
        Integer soLanChuaCanThiet,
        LocalDateTime ngayKetThuc,
        String trangThai) {
    public static final String DANG_DIEU_TRI = "Đang điều trị";
    /** Đợt kết thúc vì có lần chữa kết luận Khỏi — bác sĩ được thưởng (BR10). */
    public static final String DA_KHOI = "Đã khỏi";
    /** Bệnh nhân bỏ điều trị quá 30 ngày (BR12) — bác sĩ không được thưởng. */
    public static final String NGUNG_DIEU_TRI = "Ngừng điều trị";
}
