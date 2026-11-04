package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả lưu một lượt khám / chữa.
 *
 * @param maCDDT          khám: các đợt vừa mở; chữa: đợt của lần chữa
 * @param lanThu          chữa: lần chữa thứ mấy của đợt; khám: null
 * @param canhBao         cảnh báo không chặn việc lưu (vd không kiểm tra được BR4 ở chi nhánh khác vì S0 tắt)
 */
public record TaoLuotResponse(
        String maLuot,
        String loaiLuot,
        String maBN,
        String tenBN,
        List<String> maCDDT,
        Integer lanThu,
        BigDecimal tongTien,
        List<String> canhBao) {
}
