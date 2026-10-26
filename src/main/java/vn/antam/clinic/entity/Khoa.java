package vn.antam.clinic.entity;

/** Một dòng bảng KHOA — khoa phòng của một chi nhánh. */
public record Khoa(
        String maKhoa,
        String tenKhoa,
        String moTa,
        String maCn) {
}
