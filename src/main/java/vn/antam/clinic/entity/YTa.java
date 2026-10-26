package vn.antam.clinic.entity;

/** Một dòng bảng Y_TA (chuyên biệt hoá từ NHAN_VIEN, cùng khoá MaNV). */
public record YTa(
        String maNV,
        String trinhDo,
        String soChungChiHanhNghe) {
}
