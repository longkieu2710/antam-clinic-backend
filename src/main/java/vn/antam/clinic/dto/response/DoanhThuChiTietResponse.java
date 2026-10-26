package vn.antam.clinic.dto.response;

import java.math.BigDecimal;

/**
 * Doanh thu theo từng dịch vụ / thuốc / thiết bị trong kỳ.
 *
 * @param loai DICH_VU | THUOC | THIET_BI
 * @param nhom loại dịch vụ hoặc loại thiết bị (null với thuốc)
 */
public record DoanhThuChiTietResponse(String loai, String ma, String ten, String nhom, long soLuong, BigDecimal soTien) {
}
