package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Bác sĩ = NHAN_VIEN (LoaiNV = 'BS') ⨝ BAC_SI, kèm số điện thoại (NV_SODIENTHOAI) và số lượt đã khám/chữa. */
public record BacSiResponse(
        String maNV,
        String hoTen,
        LocalDate ngaySinh,
        String gioiTinh,
        String cccd,
        String diaChi,
        BigDecimal heSoLuong,
        LocalDate ngayVaoLam,
        String trangThai,
        String maKhoa,
        String tenKhoa,
        String chuyenKhoa,
        String hocVi,
        String soChungChiHanhNghe,
        List<String> soDienThoai,
        long soLuot) {
}
