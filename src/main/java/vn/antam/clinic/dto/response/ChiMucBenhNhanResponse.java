package vn.antam.clinic.dto.response;

/**
 * Một dòng của hàm tim_ma_benh_nhan ở S0: bệnh nhân khớp đúng số CCCD / thẻ BHYT có hồ sơ ở chi nhánh nào.
 * Không có họ tên, CCCD hay bệnh sử — chi nhánh chỉ biết mã để lập hồ sơ của mình với cùng mã.
 *
 * @param maBN   null khi {@code ketNoi} = false
 * @param ketNoi false: không tra được chi nhánh {@code maCn} (đang mất kết nối)
 */
public record ChiMucBenhNhanResponse(String maBN, String maCn, boolean ketNoi) {
}
