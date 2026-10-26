package vn.antam.clinic.entity;

/** Một dòng bảng LAN_CHUA_HINH_THUC — thuộc tính đa trị của LAN_CHUA. Khoá chính (MaLuot, HinhThucChua). */
public record LanChuaHinhThuc(
        String maLuot,
        String hinhThucChua) {
}
