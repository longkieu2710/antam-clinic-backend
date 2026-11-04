package vn.antam.clinic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.ThietBiResponse;
import vn.antam.clinic.entity.ThietBi;

/** Bảng THIET_BI của chi nhánh đang chọn. */
@Repository
public class ThietBiRepository {

    /**
     * Gom số liệu bằng CTE GROUP BY (không truy vấn con theo từng dòng): câu này còn chạy ở S0 trên view toàn cục qua
     * postgres_fdw (/api/hq/**), mỗi bảng chỉ quét qua FDW một lần.
     */
    private static final String SELECT = """
            WITH sd AS (SELECT matb, sum(solan) AS solansudung FROM su_dung_tb GROUP BY matb)
            SELECT tb.matb, tb.tentb, tb.loaitb, tb.dongiasudung, tb.ngaymua, tb.tinhtrang, tb.macn,
                   COALESCE(sd.solansudung, 0) AS solansudung
            FROM thiet_bi tb
            LEFT JOIN sd ON sd.matb = tb.matb
            """;

    private final JdbcClient jdbc;

    public ThietBiRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param q tìm theo mã, tên hoặc loại thiết bị; bỏ trống = tất cả */
    public List<ThietBiResponse> search(String q) {
        if (q == null || q.isBlank()) {
            return jdbc.sql(SELECT + " ORDER BY tb.matb").query(ThietBiResponse.class).list();
        }
        return jdbc.sql(SELECT + " WHERE tb.matb ILIKE :q OR tb.tentb ILIKE :q OR tb.loaitb ILIKE :q ORDER BY tb.matb")
                .param("q", "%" + q.trim() + "%")
                .query(ThietBiResponse.class)
                .list();
    }

    public Optional<ThietBiResponse> findById(String maTB) {
        return jdbc.sql(SELECT + " WHERE tb.matb = :maTB")
                .param("maTB", maTB)
                .query(ThietBiResponse.class)
                .optional();
    }

    public List<String> findAllMaTB() {
        return jdbc.sql("SELECT matb FROM thiet_bi").query(String.class).list();
    }

    public void insert(ThietBi tb) {
        jdbc.sql("""
                        INSERT INTO thiet_bi (matb, tentb, loaitb, dongiasudung, ngaymua, tinhtrang, macn)
                        VALUES (:maTB, :tenTB, :loaiTB, :donGiaSuDung, :ngayMua, :tinhTrang, :maCn)""")
                .paramSource(tb)
                .update();
    }

    /** @return số dòng được sửa (0 = không có thiết bị này) */
    public int update(ThietBi tb) {
        return jdbc.sql("""
                        UPDATE thiet_bi SET tentb = :tenTB, loaitb = :loaiTB, dongiasudung = :donGiaSuDung,
                                            ngaymua = :ngayMua, tinhtrang = :tinhTrang
                         WHERE matb = :maTB""")
                .paramSource(tb)
                .update();
    }

    public void updateTinhTrang(String maTB, String tinhTrang) {
        jdbc.sql("UPDATE thiet_bi SET tinhtrang = :tinhTrang WHERE matb = :maTB")
                .param("maTB", maTB)
                .param("tinhTrang", tinhTrang)
                .update();
    }

    public int delete(String maTB) {
        return jdbc.sql("DELETE FROM thiet_bi WHERE matb = :maTB").param("maTB", maTB).update();
    }
}
