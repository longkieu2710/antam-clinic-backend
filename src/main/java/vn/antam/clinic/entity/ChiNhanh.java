package vn.antam.clinic.entity;

/** Một dòng bảng CHI_NHANH — danh mục chung, bản gốc ở S0. */
public record ChiNhanh(
        String maCn,
        String tenCn,
        String diaChi,
        String soDienThoai) {
}
