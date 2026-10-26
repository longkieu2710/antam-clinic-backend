package vn.antam.clinic.entity;

/** Một dòng bảng BENH — danh mục bệnh (ICD-10); danh mục chung, bản gốc ở S0. */
public record Benh(
        String maBenh,
        String tenBenh,
        String maICD10,
        String moTa) {
}
