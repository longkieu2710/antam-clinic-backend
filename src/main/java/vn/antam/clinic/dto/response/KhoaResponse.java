package vn.antam.clinic.dto.response;

/**
 * Khoa của chi nhánh, kèm số nhân viên và số lượt khám/chữa (đọc thẳng từ truy vấn tổng hợp, không qua entity).
 */
public record KhoaResponse(String maKhoa, String tenKhoa, String moTa, String maCn, long soNhanVien, long soLuot) {
}
