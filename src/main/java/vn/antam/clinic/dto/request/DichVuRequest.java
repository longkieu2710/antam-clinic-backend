package vn.antam.clinic.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm / sửa dịch vụ y tế trong danh mục chung (ghi tại S0).
 *
 * @param maDV   bỏ trống khi thêm mới để hệ thống tự sinh (DV01, DV02...); bị bỏ qua khi sửa
 * @param loaiDV "Khám bệnh" được tính là tiền khám trong báo cáo doanh thu; loại khác tính là dịch vụ y tế
 */
public record DichVuRequest(
        @Size(max = 20) String maDV,
        @NotBlank(message = "Nhập tên dịch vụ") @Size(max = 150) String tenDV,
        @Size(max = 100) String loaiDV,
        @NotBlank(message = "Nhập đơn vị tính") @Size(max = 30) String donViTinh,
        @NotNull(message = "Nhập đơn giá") @DecimalMin(value = "0", message = "Đơn giá phải ≥ 0") BigDecimal donGia) {
}
