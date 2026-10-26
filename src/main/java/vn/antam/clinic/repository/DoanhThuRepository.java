package vn.antam.clinic.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.DoanhThuChiTietResponse;
import vn.antam.clinic.dto.response.DoanhThuThangResponse;
import vn.antam.clinic.entity.DichVu;

/** Truy vấn doanh thu (F5) trên chi nhánh đang chọn. Các lượt được lọc theo LUOT_KHAM_CHUA.ThoiGian ∈ [tu, den). */
@Repository
public class DoanhThuRepository {

    private final JdbcClient jdbc;

    public DoanhThuRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Doanh thu từng tháng: tiền khám, dịch vụ khác, thuốc, thiết bị (SoLuong / SoLan × DonGiaApDung). */
    public List<DoanhThuThangResponse> theoThang(LocalDateTime tu, LocalDateTime den) {
        return jdbc.sql("""
                        WITH luot AS (
                            SELECT maluot, to_char(thoigian, 'YYYY-MM') AS thang
                            FROM luot_kham_chua WHERE thoigian >= :tu AND thoigian < :den
                        ),
                        dv AS (
                            SELECT l.thang,
                                   sum(CASE WHEN d2.loaidv = :loaiKham THEN d.soluong * d.dongiaapdung ELSE 0 END) AS kham,
                                   sum(CASE WHEN d2.loaidv = :loaiKham THEN 0 ELSE d.soluong * d.dongiaapdung END) AS dichvu
                            FROM su_dung_dv d
                            JOIN luot l ON l.maluot = d.maluot
                            LEFT JOIN dich_vu d2 ON d2.madv = d.madv
                            GROUP BY l.thang
                        ),
                        th AS (
                            SELECT l.thang, sum(k.soluong * k.dongiaapdung) AS thuoc
                            FROM ke_don k JOIN luot l ON l.maluot = k.maluot GROUP BY l.thang
                        ),
                        tb AS (
                            SELECT l.thang, sum(s.solan * s.dongiaapdung) AS thietbi
                            FROM su_dung_tb s JOIN luot l ON l.maluot = s.maluot GROUP BY l.thang
                        ),
                        sl AS (SELECT thang, count(*) AS soluot FROM luot GROUP BY thang)
                        SELECT sl.thang, sl.soluot,
                               COALESCE(dv.kham, 0) AS tienkham,
                               COALESCE(dv.dichvu, 0) AS tiendichvu,
                               COALESCE(th.thuoc, 0) AS tienthuoc,
                               COALESCE(tb.thietbi, 0) AS tienthietbi,
                               COALESCE(dv.kham, 0) + COALESCE(dv.dichvu, 0) + COALESCE(th.thuoc, 0)
                                   + COALESCE(tb.thietbi, 0) AS tong
                        FROM sl
                        LEFT JOIN dv ON dv.thang = sl.thang
                        LEFT JOIN th ON th.thang = sl.thang
                        LEFT JOIN tb ON tb.thang = sl.thang
                        ORDER BY sl.thang""")
                .param("tu", tu)
                .param("den", den)
                .param("loaiKham", DichVu.LOAI_KHAM_BENH)
                .query(DoanhThuThangResponse.class)
                .list();
    }

    /** Doanh thu theo từng dịch vụ / thuốc / thiết bị, nhiều tiền nhất trước. */
    public List<DoanhThuChiTietResponse> chiTiet(LocalDateTime tu, LocalDateTime den) {
        return jdbc.sql("""
                        WITH luot AS (
                            SELECT maluot FROM luot_kham_chua WHERE thoigian >= :tu AND thoigian < :den
                        )
                        SELECT 'DICH_VU' AS loai, d.madv AS ma, COALESCE(dv.tendv, d.madv) AS ten, dv.loaidv AS nhom,
                               sum(d.soluong) AS soluong, sum(d.soluong * d.dongiaapdung) AS sotien
                        FROM su_dung_dv d JOIN luot l ON l.maluot = d.maluot
                        LEFT JOIN dich_vu dv ON dv.madv = d.madv
                        GROUP BY d.madv, dv.tendv, dv.loaidv
                        UNION ALL
                        SELECT 'THUOC', k.mathuoc, COALESCE(t.tenthuoc, k.mathuoc), NULL,
                               sum(k.soluong), sum(k.soluong * k.dongiaapdung)
                        FROM ke_don k JOIN luot l ON l.maluot = k.maluot
                        LEFT JOIN thuoc t ON t.mathuoc = k.mathuoc
                        GROUP BY k.mathuoc, t.tenthuoc
                        UNION ALL
                        SELECT 'THIET_BI', s.matb, COALESCE(tb.tentb, s.matb), tb.loaitb,
                               sum(s.solan), sum(s.solan * s.dongiaapdung)
                        FROM su_dung_tb s JOIN luot l ON l.maluot = s.maluot
                        LEFT JOIN thiet_bi tb ON tb.matb = s.matb
                        GROUP BY s.matb, tb.tentb, tb.loaitb
                        ORDER BY sotien DESC, ten""")
                .param("tu", tu)
                .param("den", den)
                .query(DoanhThuChiTietResponse.class)
                .list();
    }
}
