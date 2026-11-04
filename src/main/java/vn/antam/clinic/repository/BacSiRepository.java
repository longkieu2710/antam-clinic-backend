package vn.antam.clinic.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.BacSiResponse;
import vn.antam.clinic.entity.BacSi;

/**
 * Bảng BAC_SI, chạy trên trạm chi nhánh đang chọn.
 * Truy vấn đọc ghép NHAN_VIEN ⨝ BAC_SI ⨝ KHOA nên trả thẳng {@link BacSiResponse} (projection), không qua entity.
 */
@Repository
public class BacSiRepository {

    /**
     * Gom số liệu bằng CTE GROUP BY (không truy vấn con theo từng dòng): câu này còn chạy ở S0 trên view toàn cục qua
     * postgres_fdw (/api/hq/**), mỗi bảng chỉ quét qua FDW một lần.
     */
    private static final String SELECT = """
            WITH sdt AS (SELECT manv, string_agg(sodienthoai, ',' ORDER BY sodienthoai) AS sodienthoai
                           FROM nv_sodienthoai GROUP BY manv),
                 luot AS (SELECT mabs, count(*) AS soluot FROM luot_kham_chua GROUP BY mabs)
            SELECT nv.manv, nv.hoten, nv.ngaysinh, nv.gioitinh, nv.cccd, nv.diachi, nv.hesoluong,
                   nv.ngayvaolam, nv.trangthai, nv.makhoa, k.tenkhoa, k.macn,
                   bs.chuyenkhoa, bs.hocvi, bs.sochungchihanhnghe,
                   sdt.sodienthoai, COALESCE(luot.soluot, 0) AS soluot
            FROM nhan_vien nv
            JOIN bac_si bs ON bs.manv = nv.manv
            LEFT JOIN khoa k ON k.makhoa = nv.makhoa
            LEFT JOIN sdt ON sdt.manv = nv.manv
            LEFT JOIN luot ON luot.mabs = nv.manv
            WHERE nv.loainv = 'BS'
            """;

    private final JdbcClient jdbc;

    public BacSiRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<BacSiResponse> search(String q, String maKhoa) {
        StringBuilder sql = new StringBuilder(SELECT);
        Map<String, Object> params = new LinkedHashMap<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (nv.manv ILIKE :q OR nv.hoten ILIKE :q OR nv.cccd ILIKE :q OR bs.sochungchihanhnghe ILIKE :q)");
            params.put("q", "%" + q.trim() + "%");
        }
        if (maKhoa != null && !maKhoa.isBlank()) {
            sql.append(" AND nv.makhoa = :maKhoa");
            params.put("maKhoa", maKhoa);
        }
        sql.append(" ORDER BY nv.manv");
        return jdbc.sql(sql.toString()).params(params).query(BacSiRepository::map).list();
    }

    public Optional<BacSiResponse> findById(String maNV) {
        return jdbc.sql(SELECT + " AND nv.manv = :maNV")
                .param("maNV", maNV)
                .query(BacSiRepository::map)
                .optional();
    }

    public void insert(BacSi bs) {
        jdbc.sql("""
                        INSERT INTO bac_si (manv, chuyenkhoa, hocvi, sochungchihanhnghe)
                        VALUES (:maNV, :chuyenKhoa, :hocVi, :soChungChiHanhNghe)""")
                .paramSource(bs)
                .update();
    }

    public void update(BacSi bs) {
        jdbc.sql("""
                        UPDATE bac_si SET chuyenkhoa = :chuyenKhoa, hocvi = :hocVi,
                                          sochungchihanhnghe = :soChungChiHanhNghe
                         WHERE manv = :maNV""")
                .paramSource(bs)
                .update();
    }

    public long countLuot(String maNV) {
        return jdbc.sql("SELECT count(*) FROM luot_kham_chua WHERE mabs = :maNV")
                .param("maNV", maNV)
                .query(Long.class)
                .single();
    }

    public void delete(String maNV) {
        jdbc.sql("DELETE FROM bac_si WHERE manv = :maNV").param("maNV", maNV).update();
    }

    private static BacSiResponse map(ResultSet rs, int rowNum) throws SQLException {
        String phones = rs.getString("sodienthoai");
        return new BacSiResponse(
                rs.getString("manv"),
                rs.getString("hoten"),
                rs.getObject("ngaysinh", LocalDate.class),
                rs.getString("gioitinh"),
                rs.getString("cccd"),
                rs.getString("diachi"),
                rs.getBigDecimal("hesoluong"),
                rs.getObject("ngayvaolam", LocalDate.class),
                rs.getString("trangthai"),
                rs.getString("makhoa"),
                rs.getString("tenkhoa"),
                rs.getString("chuyenkhoa"),
                rs.getString("hocvi"),
                rs.getString("sochungchihanhnghe"),
                phones == null ? List.of() : Arrays.asList(phones.split(",")),
                rs.getLong("soluot"),
                rs.getString("macn"));
    }
}
