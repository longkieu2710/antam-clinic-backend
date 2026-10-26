package vn.antam.clinic.dto.response;

import java.util.List;

/**
 * F2 — các bệnh mắc phải trong một tháng tại chi nhánh, sắp xếp giảm dần theo số lần mắc.
 *
 * @param thang      dạng yyyy-MM
 * @param tongLanMac tổng số lần mắc của mọi bệnh trong tháng
 */
public record ThongKeBenhResponse(String thang, long tongLanMac, List<ThongKeBenhItemResponse> benh) {
}
