package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả lưu một lượt khám / chữa.
 *
 * @param maCDDT          khám: các đợt vừa mở; chữa: đợt của lần chữa
 * @param lanThu          chữa: lần chữa thứ mấy của đợt; khám: null
 * @param ketThucDot      chữa: lần này kết luận "Khỏi" nên đợt chuyển "Đã khỏi" (BR10)
 * @param daChepBenhNhan  khám: bệnh nhân chưa có hồ sơ ở chi nhánh nên đã chép từ view toàn chuỗi (giữ mã)
 * @param canhBao         cảnh báo không chặn việc lưu (vd không kiểm tra được BR4 ở chi nhánh khác vì S0 tắt)
 */
public record TaoLuotResponse(
        String maLuot,
        String loaiLuot,
        String maBN,
        String tenBN,
        List<String> maCDDT,
        Integer lanThu,
        boolean ketThucDot,
        boolean daChepBenhNhan,
        BigDecimal tongTien,
        List<String> canhBao) {
}
