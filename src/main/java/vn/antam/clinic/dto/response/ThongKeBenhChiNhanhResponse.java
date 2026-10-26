package vn.antam.clinic.dto.response;

/** Số lần mắc một bệnh tại một chi nhánh trong kỳ (S0: CHAN_DOAN_DIEU_TRI.SiteNguon). */
public record ThongKeBenhChiNhanhResponse(String maBenh, String maCn, long soLanMac, long soBenhNhan) {
}
