package vn.antam.clinic.dto.response;

import java.util.Map;

/** Một bệnh trong thống kê toàn chuỗi. @param theoChiNhanh số lần mắc tại từng chi nhánh (mã CN → số lần) */
public record ThongKeBenhToanChuoiItemResponse(
        String maBenh,
        String tenBenh,
        String maICD10,
        long soLanMac,
        long soBenhNhan,
        Map<String, Long> theoChiNhanh) {
}
