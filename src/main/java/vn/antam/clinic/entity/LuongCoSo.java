package vn.antam.clinic.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Một dòng bảng LUONG_CO_SO — mức lương cơ sở theo từng thời kỳ; danh mục chung, bản gốc ở S0. */
public record LuongCoSo(
        LocalDate ngayApDung,
        BigDecimal mucLuong,
        String vanBanQuyDinh) {
}
