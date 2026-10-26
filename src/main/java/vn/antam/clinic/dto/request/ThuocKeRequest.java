package vn.antam.clinic.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Một dòng đơn thuốc (KE_DON). Đơn giá do backend lấy từ danh mục lúc lưu (BR7). */
public record ThuocKeRequest(
        @NotBlank(message = "Chọn thuốc") String maThuoc,
        @NotNull(message = "Nhập số lượng") @Min(value = 1, message = "Số lượng phải > 0 (BR8)") Integer soLuong,
        @Size(max = 255, message = "Liều dùng tối đa 255 ký tự") String lieuDung) {
}
