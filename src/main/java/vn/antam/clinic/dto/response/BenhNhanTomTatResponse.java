package vn.antam.clinic.dto.response;

import java.time.LocalDate;

/** Bệnh nhân trong danh sách toàn chuỗi (view BENH_NHAN ở S0) — dùng để chọn bệnh nhân xem hồ sơ toàn chuỗi. */
public record BenhNhanTomTatResponse(String maBN, String hoTen, LocalDate ngaySinh, String gioiTinh, String cccd,
                                     String soDienThoai) {
}
