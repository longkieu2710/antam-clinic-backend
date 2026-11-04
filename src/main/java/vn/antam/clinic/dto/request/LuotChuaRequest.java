package vn.antam.clinic.dto.request;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Tạo lượt chữa cho một đợt đang điều trị: LUOT_KHAM_CHUA (CHUA) + LAN_CHUA + LAN_CHUA_HINH_THUC + HO_TRO + chi phí.
 * Bác sĩ, khoa lấy theo lần khám mở đợt (BR2) nên không gửi lên. Tình trạng / kết luận sau chữa, số lần chữa cần thiết
 * và hẹn tái khám nhập khi sửa lượt ({@link SuaLuotChuaRequest}) — lúc tạo chưa có kết quả chữa.
 *
 * @param thoiGian null = thời điểm hiện tại
 */
public record LuotChuaRequest(
        @NotBlank(message = "Chọn đợt điều trị") String maCDDT,
        LocalDateTime thoiGian,
        @NotEmpty(message = "Chọn ít nhất một hình thức chữa") List<@NotBlank(message = "Hình thức chữa không được trống") String> hinhThuc,
        String ghiChu,
        List<@Valid YTaHoTroRequest> yTa,
        List<@Valid DichVuDungRequest> dichVu,
        List<@Valid ThuocKeRequest> thuoc,
        List<@Valid ThietBiDungRequest> thietBi) implements ChiTietLuotRequest {
}
