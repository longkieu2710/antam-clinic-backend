package vn.antam.clinic.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm / sửa thuốc (ghi tại S0).
 *
 * @param maThuoc bỏ trống khi thêm mới để hệ thống tự sinh (T01, T02...); bị bỏ qua khi sửa
 */
public record ThuocRequest(
        @Size(max = 20) String maThuoc,
        @NotBlank(message = "Nhập tên thuốc") @Size(max = 150) String tenThuoc,
        @Size(max = 150) String hoatChat,
        @NotBlank(message = "Nhập đơn vị tính") @Size(max = 30) String donViTinh,
        @NotNull(message = "Nhập đơn giá") @DecimalMin(value = "0", message = "Đơn giá phải ≥ 0") BigDecimal donGia,
        @Size(max = 100) String hangSX) {
}
