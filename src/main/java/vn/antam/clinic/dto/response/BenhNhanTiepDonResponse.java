package vn.antam.clinic.dto.response;

import java.time.LocalDate;

/**
 * Bệnh nhân có hồ sơ tại chi nhánh đang chọn, khi tiếp đón (dữ liệu của chính chi nhánh — được xem đầy đủ).
 *
 * @param soDotDangDieuTri số đợt "Đang điều trị" tại chi nhánh (> 0 thì tạo được lượt chữa)
 */
public record BenhNhanTiepDonResponse(
        String maBN,
        String hoTen,
        LocalDate ngaySinh,
        String gioiTinh,
        String cccd,
        String soDienThoai,
        String soTheBHYT,
        String diaChi,
        long soDotDangDieuTri) {
}
