package vn.antam.clinic.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm / sửa bác sĩ. Ràng buộc ở đây chỉ để báo lỗi sớm cho người dùng;
 * CSDL vẫn là chốt chặn cuối (CHECK, UNIQUE, khoá ngoại).
 *
 * @param maNV bỏ trống khi thêm mới để hệ thống tự sinh (BS-{MaCN}-nn); bị bỏ qua khi sửa
 */
public record BacSiRequest(
        @Size(max = 20) String maNV,
        @NotBlank(message = "Nhập họ tên") @Size(max = 100) String hoTen,
        @Past(message = "Ngày sinh phải ở quá khứ") LocalDate ngaySinh,
        @Pattern(regexp = "Nam|Nữ|Khác", message = "Giới tính: Nam, Nữ hoặc Khác") String gioiTinh,
        @Pattern(regexp = "\\d{12}", message = "CCCD gồm 12 chữ số") String cccd,
        @Size(max = 255) String diaChi,
        @NotNull(message = "Nhập hệ số lương")
        @DecimalMin(value = "0", inclusive = false, message = "Hệ số lương phải > 0")
        @DecimalMax(value = "99.99", message = "Hệ số lương tối đa 99.99") BigDecimal heSoLuong,
        LocalDate ngayVaoLam,
        @Size(max = 30) String trangThai,
        @NotBlank(message = "Chọn khoa") @Size(max = 20) String maKhoa,
        @Size(max = 100) String chuyenKhoa,
        @Size(max = 50) String hocVi,
        @Size(max = 50) String soChungChiHanhNghe,
        List<@Pattern(regexp = "[0-9+ .-]{8,20}", message = "Số điện thoại không hợp lệ") String> soDienThoai) {
}
