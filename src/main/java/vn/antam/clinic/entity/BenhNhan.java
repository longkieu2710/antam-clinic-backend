package vn.antam.clinic.entity;

import java.time.LocalDate;

/** Một dòng bảng BENH_NHAN — bệnh nhân đăng ký tại chi nhánh. */
public record BenhNhan(
        String maBN,
        String hoTen,
        LocalDate ngaySinh,
        String gioiTinh,
        String cccd,
        String soDienThoai,
        String diaChi,
        String soTheBHYT,
        String nhomMau,
        String tienSuDiUng) {
}
