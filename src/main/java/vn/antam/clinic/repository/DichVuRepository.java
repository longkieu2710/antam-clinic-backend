package vn.antam.clinic.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.entity.DichVu;

/** Bảng DICH_VU — bản gốc khi chạy ở S0, bản sao khi chạy ở chi nhánh. */
@Repository
public class DichVuRepository {

    private static final String SELECT = "SELECT madv, tendv, loaidv, donvitinh, dongia FROM dich_vu";

    private final JdbcClient jdbc;

    public DichVuRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param q tìm theo mã, tên hoặc loại dịch vụ; bỏ trống = tất cả */
    public List<DichVu> search(String q) {
        if (q == null || q.isBlank()) {
            return jdbc.sql(SELECT + " ORDER BY madv").query(DichVu.class).list();
        }
        return jdbc.sql(SELECT + " WHERE madv ILIKE :q OR tendv ILIKE :q OR loaidv ILIKE :q ORDER BY madv")
                .param("q", "%" + q.trim() + "%")
                .query(DichVu.class)
                .list();
    }

    public List<String> findAllMaDV() {
        return jdbc.sql("SELECT madv FROM dich_vu").query(String.class).list();
    }

    public void insert(DichVu dv) {
        jdbc.sql("""
                        INSERT INTO dich_vu (madv, tendv, loaidv, donvitinh, dongia)
                        VALUES (:maDV, :tenDV, :loaiDV, :donViTinh, :donGia)""")
                .paramSource(dv)
                .update();
    }

    /** @return số dòng được sửa (0 = không có dịch vụ này) */
    public int update(DichVu dv) {
        return jdbc.sql("""
                        UPDATE dich_vu SET tendv = :tenDV, loaidv = :loaiDV, donvitinh = :donViTinh, dongia = :donGia
                         WHERE madv = :maDV""")
                .paramSource(dv)
                .update();
    }

    public int delete(String maDV) {
        return jdbc.sql("DELETE FROM dich_vu WHERE madv = :maDV").param("maDV", maDV).update();
    }

    /**
     * Số dòng SU_DUNG_DV theo từng mã dịch vụ. Ở S0, SU_DUNG_DV là view toàn cục gộp 3 chi nhánh qua postgres_fdw,
     * nên truy vấn này lỗi khi một chi nhánh đang tắt.
     */
    public Map<String, Long> countSuDungByDichVu() {
        Map<String, Long> usage = new LinkedHashMap<>();
        jdbc.sql("SELECT madv, count(*) AS n FROM su_dung_dv GROUP BY madv")
                .query((RowCallbackHandler) rs -> usage.put(rs.getString("madv"), rs.getLong("n")));
        return usage;
    }
}
