package vn.antam.clinic.entity;

/** Một dòng bảng NV_SODIENTHOAI — thuộc tính đa trị của NHAN_VIEN. Khoá chính (MaNV, SoDienThoai). */
public record NvSoDienThoai(
        String maNV,
        String soDienThoai) {
}
