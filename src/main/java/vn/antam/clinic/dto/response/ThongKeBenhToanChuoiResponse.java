package vn.antam.clinic.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Thống kê bệnh toàn chuỗi trong một tháng (F2 gộp 3 chi nhánh tại S0).
 *
 * @param chiNhanh         mã các chi nhánh theo thứ tự hiển thị
 * @param tongTheoChiNhanh tổng số lần mắc tại từng chi nhánh
 */
public record ThongKeBenhToanChuoiResponse(
        String thang,
        long tongLanMac,
        List<String> chiNhanh,
        Map<String, Long> tongTheoChiNhanh,
        List<ThongKeBenhToanChuoiItemResponse> benh) {
}
