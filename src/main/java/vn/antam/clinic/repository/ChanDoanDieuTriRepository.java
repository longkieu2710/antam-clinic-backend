package vn.antam.clinic.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.entity.ChanDoanDieuTri;

/**
 * Bảng CHAN_DOAN_DIEU_TRI (đợt điều trị). Mỗi đợt mở từ một lần khám, gồm nhiều lần chữa sau đó.
 * Câu SELECT chỉ dùng tên bảng nên chạy được cả ở chi nhánh lẫn ở S0 (view toàn cục cùng tên qua postgres_fdw).
 */
@Repository
public class ChanDoanDieuTriRepository {

    /** Số ngày bệnh nhân không quay lại thì đợt coi như bỏ dở (BR12). */
    public static final int NGAY_BO_DO = 30;

    /**
     * Đợt + tiến trình. Các con số tổng hợp (số lần đã chữa, lần chữa gần nhất, ngày hẹn của lượt gần nhất) tính bằng
     * CTE GROUP BY trên tập đợt đã lọc thay vì truy vấn con theo từng dòng — ở S0 mỗi bảng chỉ quét qua postgres_fdw
     * một lần. %s = điều kiện WHERE trên cd (CHAN_DOAN_DIEU_TRI) / lk (LAN_KHAM mở đợt).
     */
    private static final String DOT_SELECT = """
            WITH dot AS (
                SELECT cd.macddt, cd.maluotkham, cd.mabenh, cd.mucdo, cd.solanchuacanthiet, cd.ngayketthuc, cd.trangthai,
                       lk.mabn
                FROM chan_doan_dieu_tri cd
                JOIN lan_kham lk ON lk.maluot = cd.maluotkham
                %s
            ),
            chua AS (
                SELECT lc.macddt, count(*) AS solandachua, max(l.thoigian) AS lanchuagannhat
                FROM lan_chua lc JOIN luot_kham_chua l ON l.maluot = lc.maluot
                WHERE lc.macddt IN (SELECT macddt FROM dot)
                GROUP BY lc.macddt
            ),
            hen AS (
                SELECT DISTINCT ON (y.macddt) y.macddt, y.ngayhentaikham
                FROM (
                    SELECT d.macddt, l.thoigian, l.ngayhentaikham
                    FROM dot d JOIN luot_kham_chua l ON l.maluot = d.maluotkham
                    UNION ALL
                    SELECT lc.macddt, l.thoigian, l.ngayhentaikham
                    FROM lan_chua lc JOIN luot_kham_chua l ON l.maluot = lc.maluot
                    WHERE lc.macddt IN (SELECT macddt FROM dot)
                ) y
                ORDER BY y.macddt, y.thoigian DESC
            )
            SELECT x.*, COALESCE(x.lanchuagannhat, x.ngaybatdau) AS luotgannhat,
                   COALESCE(x.trangthai = :dangDieuTri AND COALESCE(x.lanchuagannhat, x.ngaybatdau) < :hanBoDo, false) AS quahan
            FROM (
                SELECT d.macddt, d.mabenh, b.tenbenh, b.maicd10, d.mucdo, d.solanchuacanthiet,
                       COALESCE(c.solandachua, 0) AS solandachua, d.trangthai, l.thoigian AS ngaybatdau, d.ngayketthuc,
                       d.maluotkham, d.mabn, bn.hoten AS tenbn, l.mabs, nv.hoten AS tenbs, l.makhoa, k.tenkhoa, k.macn,
                       c.lanchuagannhat, h.ngayhentaikham
                FROM dot d
                JOIN luot_kham_chua l ON l.maluot = d.maluotkham
                LEFT JOIN chua c ON c.macddt = d.macddt
                LEFT JOIN hen h ON h.macddt = d.macddt
                LEFT JOIN benh b ON b.mabenh = d.mabenh
                LEFT JOIN benh_nhan bn ON bn.mabn = d.mabn
                LEFT JOIN nhan_vien nv ON nv.manv = l.mabs
                LEFT JOIN khoa k ON k.makhoa = l.makhoa
            ) x
            """;

    private final JdbcClient jdbc;

    public ChanDoanDieuTriRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Mọi đợt của một bệnh nhân, mới nhất trước. */
    public List<DotDieuTriResponse> findByBenhNhan(String maBN) {
        return select("WHERE lk.mabn = :maBN", "ORDER BY x.ngaybatdau DESC, x.macddt")
                .param("maBN", maBN)
                .query(DotDieuTriResponse.class)
                .list();
    }

    /** Các đợt "Đang điều trị" của bệnh nhân — đợt nào cũng có thể thêm lần chữa. */
    public List<DotDieuTriResponse> findDangDieuTri(String maBN) {
        return select("WHERE lk.mabn = :maBN AND cd.trangthai = :dangDieuTri", "ORDER BY x.ngaybatdau, x.macddt")
                .param("maBN", maBN)
                .query(DotDieuTriResponse.class)
                .list();
    }

    /** Các đợt được mở từ một lần khám. */
    public List<DotDieuTriResponse> findByLuotKham(String maLuotKham) {
        return select("WHERE cd.maluotkham = :maLuot", "ORDER BY x.macddt")
                .param("maLuot", maLuotKham)
                .query(DotDieuTriResponse.class)
                .list();
    }

    public Optional<DotDieuTriResponse> findById(String maCDDT) {
        return select("WHERE cd.macddt = :maCDDT", "")
                .param("maCDDT", maCDDT)
                .query(DotDieuTriResponse.class)
                .optional();
    }

    /** Đợt "Đang điều trị" của cả chi nhánh (tổng quan: lịch hẹn, đợt quá hạn BR12), sắp theo ngày hẹn. */
    public List<DotDieuTriResponse> findAllDangDieuTri() {
        return select("WHERE cd.trangthai = :dangDieuTri", "ORDER BY x.ngayhentaikham NULLS LAST, luotgannhat")
                .query(DotDieuTriResponse.class)
                .list();
    }

    public List<String> findAllMaCDDT() {
        return jdbc.sql("SELECT macddt FROM chan_doan_dieu_tri").query(String.class).list();
    }

    public void insert(ChanDoanDieuTri dot) {
        jdbc.sql("""
                        INSERT INTO chan_doan_dieu_tri (macddt, maluotkham, mabenh, mucdo, solanchuacanthiet,
                                                        ngayketthuc, trangthai)
                        VALUES (:maCDDT, :maLuotKham, :maBenh, :mucDo, :soLanChuaCanThiet, :ngayKetThuc, :trangThai)""")
                .paramSource(dot)
                .update();
    }

    /** Cập nhật số lần chữa cần thiết và trạng thái (kết thúc đợt / mở lại đợt). */
    public int updateTienTrinh(String maCDDT, int soLanChuaCanThiet, String trangThai, LocalDateTime ngayKetThuc) {
        return jdbc.sql("""
                        UPDATE chan_doan_dieu_tri
                           SET solanchuacanthiet = :soLan, trangthai = :trangThai, ngayketthuc = :ngayKetThuc
                         WHERE macddt = :maCDDT""")
                .param("soLan", soLanChuaCanThiet)
                .param("trangThai", trangThai)
                .param("ngayKetThuc", ngayKetThuc)
                .param("maCDDT", maCDDT)
                .update();
    }

    /** Số lần chữa đã có của đợt — lần chữa mới là lần thứ (số này + 1). */
    public int countLanChua(String maCDDT) {
        return jdbc.sql("SELECT count(*) FROM lan_chua WHERE macddt = :maCDDT")
                .param("maCDDT", maCDDT)
                .query(Integer.class)
                .single();
    }

    private JdbcClient.StatementSpec select(String where, String orderBy) {
        return jdbc.sql(DOT_SELECT.formatted(where) + orderBy)
                .param("dangDieuTri", ChanDoanDieuTri.DANG_DIEU_TRI)
                .param("hanBoDo", LocalDateTime.now().minusDays(NGAY_BO_DO));
    }
}
