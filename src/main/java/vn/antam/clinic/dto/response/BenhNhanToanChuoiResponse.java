package vn.antam.clinic.dto.response;

import java.time.LocalDate;

/**
 * Kết quả tra cứu bệnh nhân toàn chuỗi qua view TRA_CUU_BENH_NHAN_TOAN_CHUOI
 * (chi nhánh → postgres_fdw → S0 → postgres_fdw → 3 chi nhánh).
 *
 * @param coTaiChiNhanh    true nếu bệnh nhân đã có hồ sơ ở chi nhánh đang chọn
 * @param soDotDangDieuTri số đợt "Đang điều trị" tại chi nhánh đang chọn (> 0 thì tạo được lượt chữa)
 */
public record BenhNhanToanChuoiResponse(
        String maBN,
        String hoTen,
        LocalDate ngaySinh,
        String gioiTinh,
        String cccd,
        String soDienThoai,
        String soTheBHYT,
        String diaChi,
        boolean coTaiChiNhanh,
        long soDotDangDieuTri) {
}
