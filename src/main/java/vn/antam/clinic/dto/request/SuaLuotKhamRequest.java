package vn.antam.clinic.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/**
 * Sửa lượt khám: chỉ kết luận và ngày hẹn tái khám (hai ô này không nhập lúc tạo lượt).
 *
 * @param ketLuanKham    bỏ trống = tên các bệnh đã chẩn đoán, hoặc "Không phát hiện bệnh"
 * @param ngayHenTaiKham null = không hẹn; phải sau ngày khám
 */
public record SuaLuotKhamRequest(
        @Size(max = 2000, message = "Kết luận tối đa 2000 ký tự") String ketLuanKham,
        LocalDate ngayHenTaiKham) {
}
