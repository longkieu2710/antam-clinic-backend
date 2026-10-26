package vn.antam.clinic.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm / sửa một mức lương cơ sở (ghi tại S0).
 *
 * @param ngayApDung khoá chính; bị bỏ qua khi sửa (ngày lấy theo đường dẫn)
 */
public record LuongCoSoRequest(
        @NotNull(message = "Chọn ngày áp dụng") LocalDate ngayApDung,
        @NotNull(message = "Nhập mức lương")
        @DecimalMin(value = "0", inclusive = false, message = "Mức lương phải > 0") BigDecimal mucLuong,
        @Size(max = 255) String vanBanQuyDinh) {
}
