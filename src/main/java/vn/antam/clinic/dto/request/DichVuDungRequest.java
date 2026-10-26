package vn.antam.clinic.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Dịch vụ / nhân công dùng trong lượt (SU_DUNG_DV). Đơn giá do backend lấy từ danh mục lúc lưu (BR7). */
public record DichVuDungRequest(
        @NotBlank(message = "Chọn dịch vụ") String maDV,
        @NotNull(message = "Nhập số lượng") @Min(value = 1, message = "Số lượng phải > 0 (BR8)") Integer soLuong,
        String ketQua) {
}
