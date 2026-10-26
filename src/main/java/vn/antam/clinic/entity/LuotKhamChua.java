package vn.antam.clinic.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một dòng bảng LUOT_KHAM_CHUA — lớp cha (ISA) của LAN_KHAM và LAN_CHUA.
 *
 * @param loaiLuot {@link #LOAI_KHAM} hoặc {@link #LOAI_CHUA}
 * @param maBS     bác sĩ phụ trách (BAC_SI.MaNV)
 */
public record LuotKhamChua(
        String maLuot,
        LocalDateTime thoiGian,
        String loaiLuot,
        LocalDate ngayHenTaiKham,
        String ghiChu,
        String maKhoa,
        String maBS) {

    public static final String LOAI_KHAM = "KHAM";
    public static final String LOAI_CHUA = "CHUA";
}
