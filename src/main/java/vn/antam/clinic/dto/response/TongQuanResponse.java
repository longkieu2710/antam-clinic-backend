package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tổng quan chi nhánh trong ngày / tháng hiện tại.
 *
 * @param benhNhanMoiThang bệnh nhân có lần khám đầu tiên tại chi nhánh nằm trong tháng
 * @param lichHen          đợt đang điều trị có ngày hẹn tái khám từ hôm nay trở đi, gần nhất trước
 * @param quaHan           đợt đang điều trị quá 30 ngày bệnh nhân không quay lại (BR12) — nên chuyển "Ngừng điều trị"
 */
public record TongQuanResponse(
        LocalDate ngay,
        long khamHomNay,
        long chuaHomNay,
        long dotDangDieuTri,
        long soLuotThang,
        BigDecimal doanhThuThang,
        long benhNhanMoiThang,
        List<LuotResponse> luotGanDay,
        List<DotDieuTriResponse> lichHen,
        List<DotDieuTriResponse> quaHan,
        List<ThongKeBenhItemResponse> topBenh) {
}
