package vn.antam.clinic.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.ChiPhiResponse;
import vn.antam.clinic.dto.response.LuotHoSoResponse;

/**
 * Truy vấn hồ sơ bệnh nhân (F4) trên chi nhánh đang chọn — hoặc trên S0 cho hồ sơ toàn chuỗi, vì các view toàn cục
 * ở S0 trùng tên bảng (mã lượt / mã đợt có mã chi nhánh nên không trùng giữa các chi nhánh).
 * Lượt của bệnh nhân = lần khám (LAN_KHAM.MaBN) ∪ lần chữa thuộc các đợt mở từ những lần khám đó.
 */
@Repository
public class HoSoBenhNhanRepository {

    /** Tập mã lượt (và đợt, với lần chữa) của một bệnh nhân. */
    private static final String LUOT_CUA_BN = """
            luot_bn AS (
                SELECT lk.maluot FROM lan_kham lk WHERE lk.mabn = :maBN
                UNION ALL
                SELECT lc.maluot
                FROM lan_chua lc
                JOIN chan_doan_dieu_tri cd ON cd.macddt = lc.macddt
                JOIN lan_kham lk ON lk.maluot = cd.maluotkham
                WHERE lk.mabn = :maBN
            )""";

    private final JdbcClient jdbc;

    public HoSoBenhNhanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Mọi lượt khám/chữa, mới nhất trước, kèm tiền dịch vụ / thuốc / thiết bị của từng lượt (chưa có chi tiết).
     * Chuỗi tổng hợp và tiền tính bằng CTE GROUP BY (không truy vấn con theo từng lượt) để ở S0 mỗi bảng chỉ quét một lần.
     */
    public List<LuotHoSoResponse> findLuot(String maBN) {
        return jdbc.sql("WITH " + LUOT_CUA_BN + """
                        ,
                        benh_kham AS (
                            SELECT c2.maluotkham AS maluot, string_agg(b2.tenbenh, ', ' ORDER BY b2.tenbenh) AS benh
                            FROM chan_doan_dieu_tri c2 JOIN benh b2 ON b2.mabenh = c2.mabenh
                            WHERE c2.maluotkham IN (SELECT maluot FROM luot_bn)
                            GROUP BY c2.maluotkham
                        ),
                        hinh_thuc AS (
                            SELECT h.maluot, string_agg(h.hinhthucchua, ', ' ORDER BY h.hinhthucchua) AS hinhthucchua
                            FROM lan_chua_hinh_thuc h WHERE h.maluot IN (SELECT maluot FROM luot_bn)
                            GROUP BY h.maluot
                        ),
                        y_ta AS (
                            SELECT ht.maluot, string_agg(y.hoten, ', ' ORDER BY y.hoten) AS ytahotro
                            FROM ho_tro ht JOIN nhan_vien y ON y.manv = ht.mayt
                            WHERE ht.maluot IN (SELECT maluot FROM luot_bn)
                            GROUP BY ht.maluot
                        ),
                        dv AS (
                            SELECT d.maluot, sum(d.soluong * d.dongiaapdung) AS tien FROM su_dung_dv d
                            WHERE d.maluot IN (SELECT maluot FROM luot_bn) GROUP BY d.maluot
                        ),
                        th AS (
                            SELECT t.maluot, sum(t.soluong * t.dongiaapdung) AS tien FROM ke_don t
                            WHERE t.maluot IN (SELECT maluot FROM luot_bn) GROUP BY t.maluot
                        ),
                        tb AS (
                            SELECT s.maluot, sum(s.solan * s.dongiaapdung) AS tien FROM su_dung_tb s
                            WHERE s.maluot IN (SELECT maluot FROM luot_bn) GROUP BY s.maluot
                        )
                        SELECT l.maluot, l.thoigian, l.loailuot, l.makhoa, k.tenkhoa, k.macn, l.mabs, nv.hoten AS tenbs,
                               l.ngayhentaikham, l.ghichu,
                               lk.lydokham, lk.trieuchung, lk.ketluankham,
                               lc.macddt, lc.lanthu, lc.ketluansauchua, lc.tinhtrangsauchua,
                               CASE WHEN lc.maluot IS NOT NULL THEN b.tenbenh ELSE bk.benh END AS benh,
                               ht.hinhthucchua, yt.ytahotro,
                               COALESCE(dv.tien, 0) AS tiendichvu,
                               COALESCE(th.tien, 0) AS tienthuoc,
                               COALESCE(tb.tien, 0) AS tienthietbi
                        FROM luot_kham_chua l
                        LEFT JOIN khoa k ON k.makhoa = l.makhoa
                        LEFT JOIN nhan_vien nv ON nv.manv = l.mabs
                        LEFT JOIN lan_kham lk ON lk.maluot = l.maluot
                        LEFT JOIN lan_chua lc ON lc.maluot = l.maluot
                        LEFT JOIN chan_doan_dieu_tri cd ON cd.macddt = lc.macddt
                        LEFT JOIN benh b ON b.mabenh = cd.mabenh
                        LEFT JOIN benh_kham bk ON bk.maluot = l.maluot
                        LEFT JOIN hinh_thuc ht ON ht.maluot = l.maluot
                        LEFT JOIN y_ta yt ON yt.maluot = l.maluot
                        LEFT JOIN dv ON dv.maluot = l.maluot
                        LEFT JOIN th ON th.maluot = l.maluot
                        LEFT JOIN tb ON tb.maluot = l.maluot
                        WHERE l.maluot IN (SELECT maluot FROM luot_bn)
                        ORDER BY l.thoigian DESC, l.maluot""")
                .param("maBN", maBN)
                .query(HoSoBenhNhanRepository::mapLuot)
                .list();
    }

    /** Từng khoản dịch vụ / thuốc / thiết bị của mọi lượt của bệnh nhân. */
    public List<ChiPhiResponse> findChiPhi(String maBN) {
        return jdbc.sql("WITH " + LUOT_CUA_BN + """
                        SELECT d.maluot, 'DICH_VU' AS loai, d.madv AS ma, COALESCE(dv.tendv, d.madv) AS ten,
                               dv.loaidv AS ghichu, d.soluong, d.dongiaapdung AS dongia,
                               d.soluong * d.dongiaapdung AS thanhtien
                        FROM su_dung_dv d JOIN luot_bn x ON x.maluot = d.maluot
                        LEFT JOIN dich_vu dv ON dv.madv = d.madv
                        UNION ALL
                        SELECT k.maluot, 'THUOC', k.mathuoc, COALESCE(t.tenthuoc, k.mathuoc), k.lieudung, k.soluong,
                               k.dongiaapdung, k.soluong * k.dongiaapdung
                        FROM ke_don k JOIN luot_bn x ON x.maluot = k.maluot
                        LEFT JOIN thuoc t ON t.mathuoc = k.mathuoc
                        UNION ALL
                        SELECT s.maluot, 'THIET_BI', s.matb, COALESCE(tb.tentb, s.matb), tb.loaitb, s.solan,
                               s.dongiaapdung, s.solan * s.dongiaapdung
                        FROM su_dung_tb s JOIN luot_bn x ON x.maluot = s.maluot
                        LEFT JOIN thiet_bi tb ON tb.matb = s.matb
                        ORDER BY maluot, loai, ten""")
                .param("maBN", maBN)
                .query(ChiPhiResponse.class)
                .list();
    }

    static LuotHoSoResponse mapLuot(ResultSet rs, int rowNum) throws SQLException {
        var tienDichVu = rs.getBigDecimal("tiendichvu");
        var tienThuoc = rs.getBigDecimal("tienthuoc");
        var tienThietBi = rs.getBigDecimal("tienthietbi");
        return new LuotHoSoResponse(
                rs.getString("maluot"),
                rs.getObject("thoigian", LocalDateTime.class),
                rs.getString("loailuot"),
                rs.getString("makhoa"),
                rs.getString("tenkhoa"),
                rs.getString("macn"),
                rs.getString("mabs"),
                rs.getString("tenbs"),
                rs.getObject("ngayhentaikham", LocalDate.class),
                rs.getString("ghichu"),
                rs.getString("lydokham"),
                rs.getString("trieuchung"),
                rs.getString("ketluankham"),
                rs.getString("macddt"),
                (Integer) rs.getObject("lanthu"),
                rs.getString("ketluansauchua"),
                rs.getString("tinhtrangsauchua"),
                rs.getString("benh"),
                rs.getString("hinhthucchua"),
                rs.getString("ytahotro"),
                tienDichVu,
                tienThuoc,
                tienThietBi,
                tienDichVu.add(tienThuoc).add(tienThietBi),
                List.of());
    }
}
