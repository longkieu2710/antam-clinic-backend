package vn.antam.clinic.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm / sửa bệnh nhân tại chi nhánh đang chọn.
 *
 * @param maBN bỏ trống khi thêm mới để hệ thống tự sinh mã duy nhất toàn chuỗi (BNnn); nhập mã có sẵn khi
 *             bệnh nhân đã đăng ký ở chi nhánh khác (tra cứu toàn chuỗi) để giữ nguyên định danh; bị bỏ qua khi sửa
 */
public record BenhNhanRequest(
        @Size(max = 20) String maBN,
        @NotBlank(message = "Nhập họ tên") @Size(max = 100) String hoTen,
        @Past(message = "Ngày sinh phải ở quá khứ") LocalDate ngaySinh,
        @Pattern(regexp = "Nam|Nữ|Khác", message = "Giới tính: Nam, Nữ hoặc Khác") String gioiTinh,
        @Pattern(regexp = "\\d{12}", message = "CCCD gồm 12 chữ số") String cccd,
        @Pattern(regexp = "[0-9+ .-]{8,20}", message = "Số điện thoại không hợp lệ") String soDienThoai,
        @Size(max = 255) String diaChi,
        @Size(max = 30, message = "Số thẻ BHYT tối đa 30 ký tự") String soTheBHYT,
        @Pattern(regexp = "(A|B|AB|O)[+-]?", message = "Nhóm máu: A, B, AB hoặc O (có thể kèm + / -)") String nhomMau,
        @Size(max = 2000) String tienSuDiUng) {
}
