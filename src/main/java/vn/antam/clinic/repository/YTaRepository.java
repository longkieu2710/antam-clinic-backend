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
import vn.antam.clinic.dto.response.YTaResponse;
import vn.antam.clinic.entity.YTa;

/**
 * Bảng Y_TA, chạy trên trạm chi nhánh đang chọn.
 * Truy vấn đọc ghép NHAN_VIEN ⨝ Y_TA ⨝ KHOA nên trả thẳng {@link YTaResponse} (projection), không qua entity.
 */
@Repository
public class YTaRepository {

    private static final String SELECT = """
            SELECT nv.manv, nv.hoten, nv.ngaysinh, nv.gioitinh, nv.cccd, nv.diachi, nv.hesoluong,
                   nv.ngayvaolam, nv.trangthai, nv.makhoa, k.tenkhoa,
                   yt.trinhdo, yt.sochungchihanhnghe,
                   (SELECT string_agg(s.sodienthoai, ',' ORDER BY s.sodienthoai)
                      FROM nv_sodienthoai s WHERE s.manv = nv.manv) AS sodienthoai,
                   (SELECT count(*) FROM ho_tro h WHERE h.mayt = nv.manv) AS solanhotro
            FROM nhan_vien nv
            JOIN y_ta yt ON yt.manv = nv.manv
            LEFT JOIN khoa k ON k.makhoa = nv.makhoa
            WHERE nv.loainv = 'YT'
            """;

    private final JdbcClient jdbc;

    public YTaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<YTaResponse> search(String q, String maKhoa) {
        StringBuilder sql = new StringBuilder(SELECT);
        Map<String, Object> params = new LinkedHashMap<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (nv.manv ILIKE :q OR nv.hoten ILIKE :q OR nv.cccd ILIKE :q OR yt.sochungchihanhnghe ILIKE :q)");
            params.put("q", "%" + q.trim() + "%");
        }
        if (maKhoa != null && !maKhoa.isBlank()) {
            sql.append(" AND nv.makhoa = :maKhoa");
            params.put("maKhoa", maKhoa);
        }
        sql.append(" ORDER BY nv.manv");
        return jdbc.sql(sql.toString()).params(params).query(YTaRepository::map).list();
    }

    public Optional<YTaResponse> findById(String maNV) {
        return jdbc.sql(SELECT + " AND nv.manv = :maNV")
                .param("maNV", maNV)
                .query(YTaRepository::map)
                .optional();
    }

    public void insert(YTa yTa) {
        jdbc.sql("""
                        INSERT INTO y_ta (manv, trinhdo, sochungchihanhnghe)
                        VALUES (:maNV, :trinhDo, :soChungChiHanhNghe)""")
                .paramSource(yTa)
                .update();
    }

    public void update(YTa yTa) {
        jdbc.sql("""
                        UPDATE y_ta SET trinhdo = :trinhDo, sochungchihanhnghe = :soChungChiHanhNghe
                         WHERE manv = :maNV""")
                .paramSource(yTa)
                .update();
    }

    /** Số lượt khám/chữa y tá này đã tham gia hỗ trợ (HO_TRO). */
    public long countHoTro(String maNV) {
        return jdbc.sql("SELECT count(*) FROM ho_tro WHERE mayt = :maNV")
                .param("maNV", maNV)
                .query(Long.class)
                .single();
    }

    public void delete(String maNV) {
        jdbc.sql("DELETE FROM y_ta WHERE manv = :maNV").param("maNV", maNV).update();
    }

    private static YTaResponse map(ResultSet rs, int rowNum) throws SQLException {
        String phones = rs.getString("sodienthoai");
        return new YTaResponse(
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
                rs.getString("trinhdo"),
                rs.getString("sochungchihanhnghe"),
                phones == null ? List.of() : Arrays.asList(phones.split(",")),
                rs.getLong("solanhotro"));
    }
}
