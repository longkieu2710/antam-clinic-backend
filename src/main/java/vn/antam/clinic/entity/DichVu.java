package vn.antam.clinic.entity;

import java.math.BigDecimal;

/** Một dòng bảng DICH_VU — dịch vụ y tế và bảng giá thống nhất; danh mục chung, bản gốc ở S0. */
public record DichVu(
        String maDV,
        String tenDV,
        String loaiDV,
        String donViTinh,
        BigDecimal donGia) {
    /** LoaiDV của dịch vụ khám — doanh thu tách riêng thành "tiền khám" (F5). */
    public static final String LOAI_KHAM_BENH = "Khám bệnh";
}
