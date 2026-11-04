package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Y tá = NHAN_VIEN (LoaiNV = 'YT') ⨝ Y_TA, kèm số điện thoại và số lượt đã hỗ trợ (HO_TRO).
 *
 * @param maCn chi nhánh của khoa — phân biệt khi Trung tâm xem toàn chuỗi
 */
public record YTaResponse(
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
        String trinhDo,
        String soChungChiHanhNghe,
        List<String> soDienThoai,
        long soLanHoTro,
        String maCn) {
}
