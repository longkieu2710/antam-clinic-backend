package vn.antam.clinic.entity;

/**
 * Một dòng bảng HO_TRO — y tá hỗ trợ trong một lượt khám/chữa. Khoá chính (MaYT, MaLuot).
 *
 * @param maYT y tá (Y_TA.MaNV)
 */
public record HoTro(
        String maYT,
        String maLuot,
        String vaiTro) {
}
