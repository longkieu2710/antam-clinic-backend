package vn.antam.clinic.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.BenhNhanTomTatResponse;
import vn.antam.clinic.dto.response.DoanhThuChiNhanhThangResponse;
import vn.antam.clinic.dto.response.ThongKeBenhChiNhanhResponse;
import vn.antam.clinic.entity.BenhNhan;
import vn.antam.clinic.entity.DichVu;

/**
 * Truy vấn toàn chuỗi — CHỈ chạy ở trạm trung tâm S0 (đường dẫn /api/hq/** hoặc SiteContext.callOn(S0, …)).
 * Đọc các view toàn cục của nhóm CSDL (UNION 3 chi nhánh qua postgres_fdw: cột SiteNguon / MaCN_PhatSinh) và
 * các foreign table s1/s2/s3.*. Một chi nhánh tắt thì câu lệnh lỗi.
 */
@Repository
public class ToanChuoiRepository {

    private final JdbcClient jdbc;

    public ToanChuoiRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Hồ sơ đầy đủ của bệnh nhân từ view BENH_NHAN (DISTINCT ON MaBN qua 3 chi nhánh). */
    public Optional<BenhNhan> findBenhNhan(String maBN) {
        return jdbc.sql("""
                        SELECT mabn, hoten, ngaysinh, gioitinh, cccd, sodienthoai, diachi, sothebhyt, nhommau, tiensudiung
                        FROM benh_nhan WHERE mabn = :maBN""")
                .param("maBN", maBN)
                .query(BenhNhan.class)
                .optional();
    }

    /** Danh sách bệnh nhân toàn chuỗi. @param q tìm theo mã, họ tên, CCCD, số điện thoại; null = tất cả */
    public List<BenhNhanTomTatResponse> searchBenhNhan(String q) {
        String sql = "SELECT mabn, hoten, ngaysinh, gioitinh, cccd, sodienthoai FROM benh_nhan ";
        if (q == null) {
            return jdbc.sql(sql + " ORDER BY mabn").query(BenhNhanTomTatResponse.class).list();
        }
        return jdbc.sql(sql + """
                         WHERE mabn ILIKE :q OR hoten ILIKE :q OR cccd ILIKE :q OR sodienthoai ILIKE :q
                         ORDER BY mabn""")
                .param("q", "%" + q + "%")
                .query(BenhNhanTomTatResponse.class)
                .list();
    }

    /** Chi nhánh đang có hồ sơ BENH_NHAN của từng bệnh nhân (đọc foreign table s1/s2/s3.benh_nhan). */
    public Map<String, List<String>> chiNhanhCoHoSo(List<String> maBN) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (maBN.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        SELECT mabn, 'CN1' AS macn FROM s1.benh_nhan WHERE mabn IN (:ma)
                        UNION ALL SELECT mabn, 'CN2' FROM s2.benh_nhan WHERE mabn IN (:ma)
                        UNION ALL SELECT mabn, 'CN3' FROM s3.benh_nhan WHERE mabn IN (:ma)
                        ORDER BY 1, 2""")
                .param("ma", maBN)
                .query((rs, i) -> Map.entry(rs.getString("mabn"), rs.getString("macn")))
                .list()
                .forEach(e -> result.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return result;
    }

    /** Doanh thu từng chi nhánh × từng tháng trong [tu, den), tách tiền khám (dịch vụ loại "Khám bệnh") như F5. */
    public List<DoanhThuChiNhanhThangResponse> doanhThuTheoChiNhanh(LocalDateTime tu, LocalDateTime den) {
        return jdbc.sql("""
                        WITH luot AS (
                            SELECT maluot, macn_phatsinh AS macn, to_char(thoigian, 'YYYY-MM') AS thang
                            FROM luot_kham_chua WHERE thoigian >= :tu AND thoigian < :den
                        ),
                        dv AS (
                            SELECT l.macn, l.thang,
                                   sum(CASE WHEN d2.loaidv = :loaiKham THEN d.soluong * d.dongiaapdung ELSE 0 END) AS kham,
                                   sum(CASE WHEN d2.loaidv = :loaiKham THEN 0 ELSE d.soluong * d.dongiaapdung END) AS dichvu
                            FROM su_dung_dv d
                            JOIN luot l ON l.maluot = d.maluot AND l.macn = d.sitenguon
                            LEFT JOIN dich_vu d2 ON d2.madv = d.madv
                            GROUP BY l.macn, l.thang
                        ),
                        th AS (
                            SELECT l.macn, l.thang, sum(k.soluong * k.dongiaapdung) AS thuoc
                            FROM ke_don k JOIN luot l ON l.maluot = k.maluot AND l.macn = k.sitenguon
                            GROUP BY l.macn, l.thang
                        ),
                        tb AS (
                            SELECT l.macn, l.thang, sum(s.solan * s.dongiaapdung) AS thietbi
                            FROM su_dung_tb s JOIN luot l ON l.maluot = s.maluot AND l.macn = s.sitenguon
                            GROUP BY l.macn, l.thang
                        ),
                        sl AS (SELECT macn, thang, count(*) AS soluot FROM luot GROUP BY macn, thang)
                        SELECT sl.macn, sl.thang, sl.soluot,
                               COALESCE(dv.kham, 0) AS tienkham,
                               COALESCE(dv.dichvu, 0) AS tiendichvu,
                               COALESCE(th.thuoc, 0) AS tienthuoc,
                               COALESCE(tb.thietbi, 0) AS tienthietbi,
                               COALESCE(dv.kham, 0) + COALESCE(dv.dichvu, 0) + COALESCE(th.thuoc, 0)
                                   + COALESCE(tb.thietbi, 0) AS tong
                        FROM sl
                        LEFT JOIN dv ON dv.macn = sl.macn AND dv.thang = sl.thang
                        LEFT JOIN th ON th.macn = sl.macn AND th.thang = sl.thang
                        LEFT JOIN tb ON tb.macn = sl.macn AND tb.thang = sl.thang
                        ORDER BY sl.macn, sl.thang""")
                .param("tu", tu)
                .param("den", den)
                .param("loaiKham", DichVu.LOAI_KHAM_BENH)
                .query(DoanhThuChiNhanhThangResponse.class)
                .list();
    }

    /**
     * Số lần mắc từng bệnh tại từng chi nhánh trong [tu, den) — cùng cách đếm F2 (một đợt có lượt trong kỳ = 1 lần
     * mắc), nhóm thêm theo chi nhánh của đợt (CHAN_DOAN_DIEU_TRI.SiteNguon).
     */
    public List<ThongKeBenhChiNhanhResponse> thongKeBenhTheoChiNhanh(LocalDateTime tu, LocalDateTime den) {
        return jdbc.sql("""
                        WITH luot_trong_ky AS (
                            SELECT maluot FROM luot_kham_chua WHERE thoigian >= :tu AND thoigian < :den
                        ),
                        dot AS (
                            SELECT cd.macddt
                            FROM chan_doan_dieu_tri cd JOIN luot_trong_ky t ON t.maluot = cd.maluotkham
                            UNION
                            SELECT lc.macddt
                            FROM lan_chua lc JOIN luot_trong_ky t ON t.maluot = lc.maluot
                        )
                        SELECT cd.mabenh, cd.sitenguon AS macn,
                               count(*) AS solanmac, count(DISTINCT lk.mabn) AS sobenhnhan
                        FROM dot d
                        JOIN chan_doan_dieu_tri cd ON cd.macddt = d.macddt
                        JOIN lan_kham lk ON lk.maluot = cd.maluotkham AND lk.sitenguon = cd.sitenguon
                        GROUP BY cd.mabenh, cd.sitenguon
                        ORDER BY cd.mabenh, cd.sitenguon""")
                .param("tu", tu)
                .param("den", den)
                .query(ThongKeBenhChiNhanhResponse.class)
                .list();
    }
}
