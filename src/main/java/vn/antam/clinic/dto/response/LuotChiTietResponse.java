package vn.antam.clinic.dto.response;

import java.util.List;

/**
 * Chi tiết một lượt: thông tin chung, hình thức chữa, y tá hỗ trợ, các đợt liên quan (khám: đợt mở từ lượt này;
 * chữa: đợt của lần chữa), chi phí từng khoản.
 *
 * @param xoaDuoc      lượt có thể xoá vì nhập nhầm (xem {@code lyDoKhongXoa} khi false)
 */
public record LuotChiTietResponse(
        LuotResponse luot,
        List<String> hinhThuc,
        List<YTaHoTroResponse> yTa,
        List<DotDieuTriResponse> dotDieuTri,
        List<ChiPhiResponse> chiPhi,
        boolean xoaDuoc,
        String lyDoKhongXoa) {
}
