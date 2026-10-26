package vn.antam.clinic.dto.response;

/**
 * Một dòng thống kê bệnh trong tháng (đọc thẳng từ truy vấn tổng hợp).
 *
 * @param soLanMac   số lần mắc = số đợt điều trị có ít nhất một lượt khám/chữa trong tháng; các lượt liên tiếp của
 *                   cùng một đợt chỉ tính 1 lần, mắc lại (đợt mới) tính thêm lần
 * @param soBenhNhan số bệnh nhân khác nhau mắc bệnh này trong tháng
 */
public record ThongKeBenhItemResponse(String maBenh, String tenBenh, String maICD10, long soLanMac, long soBenhNhan) {
}
