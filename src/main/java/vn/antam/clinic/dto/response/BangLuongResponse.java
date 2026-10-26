package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * F3 — bảng lương bác sĩ, y tá của chi nhánh trong một tháng (tính trực tiếp, chưa có chốt lương vì CSDL phân tán
 * không có bảng BANG_LUONG).
 *
 * @param thang               dạng yyyy-MM
 * @param ngayApDungLuongCoSo ngày bắt đầu hiệu lực của mức lương cơ sở dùng để tính
 */
public record BangLuongResponse(
        String thang,
        LocalDate ngayApDungLuongCoSo,
        BigDecimal mucLuongCoSo,
        BigDecimal thuongMoiDotKhoi,
        BigDecimal thuongMoiLanHoTro,
        BigDecimal tongQuyLuong,
        List<LuongNhanVienResponse> nhanVien) {
}
