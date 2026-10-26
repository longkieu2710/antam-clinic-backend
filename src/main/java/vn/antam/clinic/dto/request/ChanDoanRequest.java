package vn.antam.clinic.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Một bệnh được chẩn đoán ở lần khám → mở một đợt điều trị (CHAN_DOAN_DIEU_TRI). */
public record ChanDoanRequest(
        @NotBlank(message = "Chọn bệnh") String maBenh,
        @Pattern(regexp = "Nhẹ|Trung bình|Nặng", message = "Mức độ là Nhẹ, Trung bình hoặc Nặng (BR8)") String mucDo,
        @NotNull(message = "Nhập số lần chữa cần thiết")
        @Min(value = 1, message = "Số lần chữa cần thiết ≥ 1 (BR8)") Integer soLanChuaCanThiet) {
}
