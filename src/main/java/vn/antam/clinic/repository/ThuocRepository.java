package vn.antam.clinic.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.entity.Thuoc;

/** Bảng THUOC — bản gốc khi chạy ở S0, bản sao khi chạy ở chi nhánh. */
@Repository
public class ThuocRepository {

    private static final String SELECT = "SELECT mathuoc, tenthuoc, hoatchat, donvitinh, dongia, hangsx FROM thuoc";

    private final JdbcClient jdbc;

    public ThuocRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param q tìm theo mã, tên hoặc hoạt chất; bỏ trống = tất cả */
    public List<Thuoc> search(String q) {
        if (q == null || q.isBlank()) {
            return jdbc.sql(SELECT + " ORDER BY mathuoc").query(Thuoc.class).list();
        }
        return jdbc.sql(SELECT + " WHERE mathuoc ILIKE :q OR tenthuoc ILIKE :q OR hoatchat ILIKE :q ORDER BY mathuoc")
                .param("q", "%" + q.trim() + "%")
                .query(Thuoc.class)
                .list();
    }

    public List<String> findAllMaThuoc() {
        return jdbc.sql("SELECT mathuoc FROM thuoc").query(String.class).list();
    }

    public void insert(Thuoc thuoc) {
        jdbc.sql("""
                        INSERT INTO thuoc (mathuoc, tenthuoc, hoatchat, donvitinh, dongia, hangsx)
                        VALUES (:maThuoc, :tenThuoc, :hoatChat, :donViTinh, :donGia, :hangSX)""")
                .paramSource(thuoc)
                .update();
    }

    /** @return số dòng được sửa (0 = không có thuốc này) */
    public int update(Thuoc thuoc) {
        return jdbc.sql("""
                        UPDATE thuoc SET tenthuoc = :tenThuoc, hoatchat = :hoatChat, donvitinh = :donViTinh,
                                         dongia = :donGia, hangsx = :hangSX
                         WHERE mathuoc = :maThuoc""")
                .paramSource(thuoc)
                .update();
    }

    /** @return số dòng bị xoá (0 = không có thuốc này) */
    public int delete(String maThuoc) {
        return jdbc.sql("DELETE FROM thuoc WHERE mathuoc = :ma").param("ma", maThuoc).update();
    }

    /**
     * Số dòng KE_DON theo từng mã thuốc. Ở S0, KE_DON là view toàn cục gộp 3 chi nhánh qua postgres_fdw,
     * nên truy vấn này lỗi khi một chi nhánh đang tắt.
     */
    public Map<String, Long> countKeDonByThuoc() {
        Map<String, Long> usage = new LinkedHashMap<>();
        jdbc.sql("SELECT mathuoc, count(*) AS n FROM ke_don GROUP BY mathuoc")
                .query((RowCallbackHandler) rs -> usage.put(rs.getString("mathuoc"), rs.getLong("n")));
        return usage;
    }
}
