package vn.antam.clinic.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một dòng bảng NHAN_VIEN — lớp cha (ISA) của BAC_SI và Y_TA.
 * Tên thành phần trùng tên cột nên JdbcClient tự ánh xạ hai chiều:
 * {@code .query(NhanVien.class)} khi đọc, {@code .paramSource(nhanVien)} khi ghi.
 *
 * @param loaiNV {@link #LOAI_BAC_SI} hoặc {@link #LOAI_Y_TA}
 */
public record NhanVien(
        String maNV,
        String hoTen,
        LocalDate ngaySinh,
        String gioiTinh,
        String cccd,
        String diaChi,
        BigDecimal heSoLuong,
        LocalDate ngayVaoLam,
        String trangThai,
        String loaiNV,
        String maKhoa) {

    public static final String LOAI_BAC_SI = "BS";
    public static final String LOAI_Y_TA = "YT";
    public static final String DANG_LAM_VIEC = "Đang làm việc";
    /** Nhân viên đã có lịch sử khám/chữa thì không xoá mà chuyển trạng thái này (BR9). */
    public static final String NGUNG_HOAT_DONG = "Ngừng hoạt động";
}
