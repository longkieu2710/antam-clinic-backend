package vn.antam.clinic.repository;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Các con số tổng hợp cho trang tổng quan chi nhánh. */
@Repository
public class TongQuanRepository {

    private final JdbcClient jdbc;

    public TongQuanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Số lượt loại {@code loai} (KHAM | CHUA) có thời gian trong [tu, den). */
    public long countLuot(String loai, LocalDateTime tu, LocalDateTime den) {
        return jdbc.sql("SELECT count(*) FROM luot_kham_chua WHERE loailuot = :loai AND thoigian >= :tu AND thoigian < :den")
                .param("loai", loai)
                .param("tu", tu)
                .param("den", den)
                .query(Long.class)
                .single();
    }

    /** Bệnh nhân có lần khám đầu tiên tại chi nhánh nằm trong [tu, den). */
    public long countBenhNhanMoi(LocalDateTime tu, LocalDateTime den) {
        return jdbc.sql("""
                        SELECT count(*) FROM (
                            SELECT lk.mabn, min(l.thoigian) AS dau
                            FROM lan_kham lk JOIN luot_kham_chua l ON l.maluot = lk.maluot
                            GROUP BY lk.mabn
                        ) x WHERE x.dau >= :tu AND x.dau < :den""")
                .param("tu", tu)
                .param("den", den)
                .query(Long.class)
                .single();
    }
}
