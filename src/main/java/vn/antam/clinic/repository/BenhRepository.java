package vn.antam.clinic.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.entity.Benh;

/** Bảng BENH — bản gốc khi chạy ở S0, bản sao khi chạy ở chi nhánh. */
@Repository
public class BenhRepository {

    private static final String SELECT = "SELECT mabenh, tenbenh, maicd10, mota FROM benh";

    private final JdbcClient jdbc;

    public BenhRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param q tìm theo mã, tên hoặc mã ICD-10; bỏ trống = tất cả */
    public List<Benh> search(String q) {
        if (q == null || q.isBlank()) {
            return jdbc.sql(SELECT + " ORDER BY mabenh").query(Benh.class).list();
        }
        return jdbc.sql(SELECT + " WHERE mabenh ILIKE :q OR tenbenh ILIKE :q OR maicd10 ILIKE :q ORDER BY mabenh")
                .param("q", "%" + q.trim() + "%")
                .query(Benh.class)
                .list();
    }

    public List<String> findAllMaBenh() {
        return jdbc.sql("SELECT mabenh FROM benh").query(String.class).list();
    }

    public void insert(Benh benh) {
        jdbc.sql("INSERT INTO benh (mabenh, tenbenh, maicd10, mota) VALUES (:maBenh, :tenBenh, :maICD10, :moTa)")
                .paramSource(benh)
                .update();
    }

    /** @return số dòng được sửa (0 = không có bệnh này) */
    public int update(Benh benh) {
        return jdbc.sql("UPDATE benh SET tenbenh = :tenBenh, maicd10 = :maICD10, mota = :moTa WHERE mabenh = :maBenh")
                .paramSource(benh)
                .update();
    }

    public int delete(String maBenh) {
        return jdbc.sql("DELETE FROM benh WHERE mabenh = :maBenh").param("maBenh", maBenh).update();
    }

    /**
     * Số đợt điều trị theo từng mã bệnh. Ở S0, CHAN_DOAN_DIEU_TRI là view toàn cục gộp 3 chi nhánh qua postgres_fdw,
     * nên truy vấn này lỗi khi một chi nhánh đang tắt.
     */
    public Map<String, Long> countChanDoanByBenh() {
        Map<String, Long> usage = new LinkedHashMap<>();
        jdbc.sql("SELECT mabenh, count(*) AS n FROM chan_doan_dieu_tri GROUP BY mabenh")
                .query((RowCallbackHandler) rs -> usage.put(rs.getString("mabenh"), rs.getLong("n")));
        return usage;
    }
}
