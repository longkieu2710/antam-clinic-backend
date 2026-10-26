package vn.antam.clinic.dto.request;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Tạo lượt khám: LUOT_KHAM_CHUA (KHAM) + LAN_KHAM + mỗi chẩn đoán một CHAN_DOAN_DIEU_TRI + HO_TRO + SU_DUNG_DV +
 * KE_DON + SU_DUNG_TB, lưu trong một transaction tại chi nhánh.
 *
 * @param thoiGian null = thời điểm hiện tại
 * @param chanDoan rỗng = khám không phát hiện bệnh, không mở đợt (BR13)
 */
public record LuotKhamRequest(
        @NotBlank(message = "Chọn bệnh nhân") String maBN,
        @NotBlank(message = "Chọn khoa") String maKhoa,
        @NotBlank(message = "Chọn bác sĩ khám") String maBS,
        LocalDateTime thoiGian,
        String lyDoKham,
        String trieuChung,
        String ketLuanKham,
        LocalDate ngayHenTaiKham,
        String ghiChu,
        List<@Valid ChanDoanRequest> chanDoan,
        List<@Valid YTaHoTroRequest> yTa,
        List<@Valid DichVuDungRequest> dichVu,
        List<@Valid ThuocKeRequest> thuoc,
        List<@Valid ThietBiDungRequest> thietBi) implements ChiTietLuotRequest {
}
