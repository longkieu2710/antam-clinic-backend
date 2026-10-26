package vn.antam.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dữ liệu sửa thông tin chi nhánh (không cho đổi mã). */
public record ChiNhanhRequest(
        @NotBlank @Size(max = 100) String tenCn,
        @Size(max = 255) String diaChi,
        @Size(max = 20) String soDienThoai) {
}
