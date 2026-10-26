package vn.antam.clinic.entity;

/** Một dòng bảng BAC_SI (chuyên biệt hoá từ NHAN_VIEN, cùng khoá MaNV). */
public record BacSi(
        String maNV,
        String chuyenKhoa,
        String hocVi,
        String soChungChiHanhNghe) {
}
