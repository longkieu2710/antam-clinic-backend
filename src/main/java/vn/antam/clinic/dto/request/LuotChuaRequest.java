package vn.antam.clinic.dto.request;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Tạo lượt chữa cho một đợt đang điều trị: LUOT_KHAM_CHUA (CHUA) + LAN_CHUA + LAN_CHUA_HINH_THUC + HO_TRO + chi phí,
 * và cập nhật CHAN_DOAN_DIEU_TRI. Bác sĩ, khoa lấy theo lần khám mở đợt (BR2) nên không gửi lên.
 *
 * @param thoiGian          null = thời điểm hiện tại
 * @param tinhTrangSauChua  "Khỏi" kết thúc đợt (BR10)
 * @param soLanChuaCanThiet bác sĩ điều chỉnh số lần chữa dự kiến; null = giữ nguyên
 */
public record LuotChuaRequest(
        @NotBlank(message = "Chọn đợt điều trị") String maCDDT,
        LocalDateTime thoiGian,
        @NotEmpty(message = "Chọn ít nhất một hình thức chữa") List<@NotBlank(message = "Hình thức chữa không được trống") String> hinhThuc,
        String ketLuanSauChua,
        @NotBlank(message = "Chọn tình trạng sau chữa") String tinhTrangSauChua,
        @Min(value = 1, message = "Số lần chữa cần thiết ≥ 1 (BR8)") Integer soLanChuaCanThiet,
        LocalDate ngayHenTaiKham,
        String ghiChu,
        List<@Valid YTaHoTroRequest> yTa,
        List<@Valid DichVuDungRequest> dichVu,
        List<@Valid ThuocKeRequest> thuoc,
        List<@Valid ThietBiDungRequest> thietBi) implements ChiTietLuotRequest {
}
