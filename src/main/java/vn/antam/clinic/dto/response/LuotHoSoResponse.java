package vn.antam.clinic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Một lượt khám hoặc chữa trong hồ sơ bệnh nhân, kèm tổng chi phí và chi tiết từng khoản.
 *
 * @param loaiLuot KHAM (các trường lyDoKham, trieuChung, ketLuanKham) hoặc CHUA (maCDDT, lanThu, ketLuanSauChua,
 *                 tinhTrangSauChua, hinhThucChua)
 * @param maCn     chi nhánh phát sinh lượt (theo khoa)
 * @param benh     khám: các bệnh được chẩn đoán; chữa: bệnh của đợt điều trị
 */
public record LuotHoSoResponse(
        String maLuot,
        LocalDateTime thoiGian,
        String loaiLuot,
        String maKhoa,
        String tenKhoa,
        String maCn,
        String maBS,
        String tenBS,
        LocalDate ngayHenTaiKham,
        String ghiChu,
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
        BigDecimal tongTien,
        List<ChiPhiResponse> chiTiet) {

    public LuotHoSoResponse withChiTiet(List<ChiPhiResponse> items) {
        return new LuotHoSoResponse(maLuot, thoiGian, loaiLuot, maKhoa, tenKhoa, maCn, maBS, tenBS, ngayHenTaiKham, ghiChu,
                lyDoKham, trieuChung, ketLuanKham, maCDDT, lanThu, ketLuanSauChua, tinhTrangSauChua, benh, hinhThucChua,
                yTaHoTro, tienDichVu, tienThuoc, tienThietBi, tongTien, items);
    }
}
