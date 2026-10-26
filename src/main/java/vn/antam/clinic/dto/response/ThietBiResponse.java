package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Thiết bị của chi nhánh, kèm số lần đã dùng trong lượt khám/chữa (SU_DUNG_TB). */
public record ThietBiResponse(
        String maTB,
        String tenTB,
        String loaiTB,
        BigDecimal donGiaSuDung,
        LocalDate ngayMua,
        String tinhTrang,
        String maCn,
        long soLanSuDung) {
}
