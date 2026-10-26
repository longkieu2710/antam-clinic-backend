package vn.antam.clinic.dto.response;

import java.math.BigDecimal;

/**
 * Lương một nhân viên trong tháng (tính thẳng trong câu SQL, đọc ra như projection).
 *
 * @param luongCoBan hệ số lương × mức lương cơ sở có hiệu lực, làm tròn đến đồng
 * @param soDotKhoi  bác sĩ: số đợt điều trị "Đã khỏi" có ngày kết thúc trong tháng (đợt do bác sĩ này mở)
 * @param soLanHoTro y tá: số lượt khám/chữa trong tháng có tham gia hỗ trợ (HO_TRO)
 * @param thuong     1.000.000 × soDotKhoi + 200.000 × soLanHoTro
 */
public record LuongNhanVienResponse(
        String maNV,
        String hoTen,
        String loaiNV,
        String maKhoa,
        String tenKhoa,
        String trangThai,
        BigDecimal heSoLuong,
        BigDecimal luongCoBan,
        long soDotKhoi,
        long soLanHoTro,
        BigDecimal thuong,
        BigDecimal tongLuong) {
}
