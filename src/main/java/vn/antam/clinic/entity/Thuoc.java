package vn.antam.clinic.entity;

import java.math.BigDecimal;

/** Một dòng bảng THUOC — danh mục chung, bản gốc ở S0, bản sao ở S1..S3 (Logical Replication). */
public record Thuoc(
        String maThuoc,
        String tenThuoc,
        String hoatChat,
        String donViTinh,
        BigDecimal donGia,
        String hangSX) {
}
