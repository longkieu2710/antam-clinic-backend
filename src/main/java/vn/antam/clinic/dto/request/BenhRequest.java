package vn.antam.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm / sửa bệnh trong danh mục chung (ghi tại S0).
 *
 * @param maBenh bỏ trống khi thêm mới để hệ thống tự sinh (B01, B02...); bị bỏ qua khi sửa
 */
public record BenhRequest(
        @Size(max = 20) String maBenh,
        @NotBlank(message = "Nhập tên bệnh") @Size(max = 150) String tenBenh,
        @Size(max = 20, message = "Mã ICD-10 tối đa 20 ký tự") String maICD10,
        @Size(max = 2000) String moTa) {
}
