package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một lượt khám hoặc chữa trong danh sách lượt của chi nhánh, kèm bệnh nhân và tổng tiền.
 *
 * @param loaiLuot KHAM (lyDoKham, trieuChung, ketLuanKham) hoặc CHUA (maCDDT, lanThu, ketLuanSauChua, tinhTrangSauChua)
 * @param maBN     khám: LAN_KHAM.MaBN; chữa: bệnh nhân của lần khám mở đợt
 * @param benh     khám: các bệnh được chẩn đoán; chữa: bệnh của đợt
 */
public record LuotResponse(
        String maLuot,
        LocalDateTime thoiGian,
        String loaiLuot,
        LocalDate ngayHenTaiKham,
        String ghiChu,
        String maKhoa,
        String tenKhoa,
        String maBS,
        String tenBS,
        String maBN,
        String tenBN,
        String lyDoKham,
        String trieuChung,
        String ketLuanKham,
        String maCDDT,
        Integer lanThu,
        String ketLuanSauChua,
        String tinhTrangSauChua,
        String benh,
        String hinhThucChua,
        String yTaHoTro,
        BigDecimal tienDichVu,
        BigDecimal tienThuoc,
        BigDecimal tienThietBi,
        BigDecimal tongTien) {
}
