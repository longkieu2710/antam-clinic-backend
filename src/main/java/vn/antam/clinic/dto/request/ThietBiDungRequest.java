package vn.antam.clinic.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Thiết bị dùng trong lượt (SU_DUNG_TB). Đơn giá sử dụng lấy từ THIET_BI lúc lưu (BR7). */
public record ThietBiDungRequest(
        @NotBlank(message = "Chọn thiết bị") String maTB,
        @NotNull(message = "Nhập số lần") @Min(value = 1, message = "Số lần phải > 0 (BR8)") Integer soLan) {
}
