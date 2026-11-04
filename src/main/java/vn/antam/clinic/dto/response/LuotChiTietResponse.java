package vn.antam.clinic.dto.response;

import java.util.List;

/**
 * Chi tiết một lượt: thông tin chung, hình thức chữa, y tá hỗ trợ, các đợt liên quan (khám: đợt mở từ lượt này;
 * chữa: đợt của lần chữa), chi phí từng khoản.
 *
 * @param xoaDuoc      lượt có thể xoá vì nhập nhầm (xem {@code lyDoKhongXoa} khi false)
 * @param luotChua     khám: các lượt chữa của những đợt mở từ lượt này, theo thời gian; chữa: rỗng
 * @param suaDuoc      sửa được lượt (xem {@code lyDoKhongSua} khi false) — khám: kết luận, hẹn tái khám; chữa: tình trạng,
 *                     kết luận, số lần chữa cần thiết, hẹn. Khoá khi đợt của lượt đã khỏi hoặc ngừng điều trị
 */
public record LuotChiTietResponse(
        LuotResponse luot,
        List<String> hinhThuc,
        List<YTaHoTroResponse> yTa,
        List<DotDieuTriResponse> dotDieuTri,
        List<ChiPhiResponse> chiPhi,
        boolean xoaDuoc,
        String lyDoKhongXoa,
        List<LuotResponse> luotChua,
        boolean suaDuoc,
        String lyDoKhongSua) {
}
