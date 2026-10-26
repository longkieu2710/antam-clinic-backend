package vn.antam.clinic.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm / sửa thiết bị của chi nhánh đang chọn (MaCN lấy theo chi nhánh, không gửi lên).
 *
 * @param maTB      bỏ trống khi thêm mới để hệ thống tự sinh (TB-{MaCN}-nn); bị bỏ qua khi sửa
 * @param tinhTrang bỏ trống = "Hoạt động tốt"
 */
public record ThietBiRequest(
        @Size(max = 20) String maTB,
        @NotBlank(message = "Nhập tên thiết bị") @Size(max = 150) String tenTB,
        @Size(max = 100) String loaiTB,
        @NotNull(message = "Nhập đơn giá sử dụng")
        @DecimalMin(value = "0", message = "Đơn giá sử dụng phải ≥ 0") BigDecimal donGiaSuDung,
        @PastOrPresent(message = "Ngày mua không được ở tương lai") LocalDate ngayMua,
        @Size(max = 50) String tinhTrang) {
}
