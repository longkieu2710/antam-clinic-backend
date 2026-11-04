package vn.antam.clinic.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Sửa lượt chữa: kết quả của lần chữa (không nhập lúc tạo lượt).
 *
 * @param tinhTrangSauChua  một trong LanChua.TINH_TRANG; "Khỏi" kết thúc đợt "Đã khỏi" (BR10) — chỉ ở lần chữa cuối
 * @param soLanChuaCanThiet bác sĩ điều chỉnh số lần chữa dự kiến của đợt; null = giữ nguyên
 * @param ngayHenTaiKham    hẹn tái khám tiếp, sau ngày chữa; bỏ qua khi "Khỏi"
 */
public record SuaLuotChuaRequest(
        String tinhTrangSauChua,
        @Size(max = 2000, message = "Kết luận tối đa 2000 ký tự") String ketLuanSauChua,
        @Min(value = 1, message = "Số lần chữa cần thiết ≥ 1 (BR8)") Integer soLanChuaCanThiet,
        LocalDate ngayHenTaiKham) {
}
