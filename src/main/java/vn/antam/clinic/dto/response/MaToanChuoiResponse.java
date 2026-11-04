package vn.antam.clinic.dto.response;

/**
 * Một dòng của các hàm tra cứu ở S0 chỉ trả mã (mã bệnh đang điều trị, mã bệnh nhân theo tiền tố).
 *
 * @param ma     null khi {@code ketNoi} = false
 * @param ketNoi false: một chi nhánh không tra được (đang mất kết nối)
 */
public record MaToanChuoiResponse(String ma, boolean ketNoi) {
}
