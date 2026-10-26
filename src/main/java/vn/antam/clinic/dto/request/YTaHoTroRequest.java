package vn.antam.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Một y tá hỗ trợ lượt khám/chữa (HO_TRO) — mỗi dòng được +200.000 ₫ vào lương tháng (F3). */
public record YTaHoTroRequest(
        @NotBlank(message = "Chọn y tá") String maYT,
        @Size(max = 100, message = "Vai trò tối đa 100 ký tự") String vaiTro) {
}
