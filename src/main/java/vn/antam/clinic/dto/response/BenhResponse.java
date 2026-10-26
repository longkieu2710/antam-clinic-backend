package vn.antam.clinic.dto.response;

import vn.antam.clinic.entity.Benh;

/**
 * Bệnh trả về FE.
 *
 * @param soLanChanDoan số đợt điều trị (CHAN_DOAN_DIEU_TRI) của bệnh này trên toàn chuỗi (view toàn cục tại S0);
 *                      null nếu không đọc được (một chi nhánh đang tắt)
 */
public record BenhResponse(String maBenh, String tenBenh, String maICD10, String moTa, Long soLanChanDoan) {

    public static BenhResponse from(Benh benh, Long soLanChanDoan) {
        return new BenhResponse(benh.maBenh(), benh.tenBenh(), benh.maICD10(), benh.moTa(), soLanChanDoan);
    }
}
