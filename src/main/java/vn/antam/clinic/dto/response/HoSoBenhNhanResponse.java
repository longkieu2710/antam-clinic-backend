package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * F4 — hồ sơ bệnh nhân tại chi nhánh: thông tin, các đợt điều trị (đợt "Đang điều trị" = tình trạng hiện tại),
 * toàn bộ lượt khám/chữa từ trước đến nay (mới nhất trước) và tổng chi phí.
 */
public record HoSoBenhNhanResponse(
        BenhNhanResponse benhNhan,
        List<DotDieuTriResponse> dotDieuTri,
        List<LuotHoSoResponse> luot,
        BigDecimal tongChiPhi) {
}
