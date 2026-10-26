package vn.antam.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm / sửa khoa của chi nhánh đang chọn (MaCN lấy theo chi nhánh, không gửi lên).
 *
 * @param maKhoa vd "CN1-TMH"; bỏ trống khi thêm mới để hệ thống tự sinh ({MaCN}-Knn); bị bỏ qua khi sửa
 */
public record KhoaRequest(
        @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9-]*", message = "Mã khoa chỉ gồm chữ, số và dấu gạch ngang")
        String maKhoa,
        @NotBlank(message = "Nhập tên khoa") @Size(max = 100) String tenKhoa,
        @Size(max = 2000) String moTa) {
}
