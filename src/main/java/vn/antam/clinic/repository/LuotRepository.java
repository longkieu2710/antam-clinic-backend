package vn.antam.clinic.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.ChiPhiResponse;
import vn.antam.clinic.dto.response.LuotResponse;
import vn.antam.clinic.dto.response.YTaHoTroResponse;
import vn.antam.clinic.entity.HoTro;
import vn.antam.clinic.entity.KeDon;
import vn.antam.clinic.entity.LanChua;
import vn.antam.clinic.entity.LanChuaHinhThuc;
import vn.antam.clinic.entity.LanKham;
import vn.antam.clinic.entity.LuotKhamChua;
import vn.antam.clinic.entity.SuDungDv;
import vn.antam.clinic.entity.SuDungTb;

/**
 * Lượt khám / chữa của chi nhánh đang chọn: LUOT_KHAM_CHUA và các bảng con
 * (LAN_KHAM | LAN_CHUA, LAN_CHUA_HINH_THUC, HO_TRO, SU_DUNG_DV, KE_DON, SU_DUNG_TB).
 * Chi nhánh không có trigger nên ISA (mỗi lượt có đúng 1 LAN_KHAM hoặc 1 LAN_CHUA) do service ghi trong 1 transaction.
 */
@Repository
public class LuotRepository {

    private static final String SELECT = """
            SELECT l.maluot, l.thoigian, l.loailuot, l.ngayhentaikham, l.ghichu,
                   l.makhoa, k.tenkhoa, l.mabs, nv.hoten AS tenbs,
                   COALESCE(lk.mabn, lkd.mabn) AS mabn, bn.hoten AS tenbn,
                   lk.lydokham, lk.trieuchung, lk.ketluankham,
                   lc.macddt, lc.lanthu, lc.ketluansauchua, lc.tinhtrangsauchua,
                   CASE WHEN lc.maluot IS NOT NULL THEN b.tenbenh
                        ELSE (SELECT string_agg(b2.tenbenh, ', ' ORDER BY b2.tenbenh)
                                FROM chan_doan_dieu_tri c2 JOIN benh b2 ON b2.mabenh = c2.mabenh
                               WHERE c2.maluotkham = l.maluot) END AS benh,
                   (SELECT string_agg(h.hinhthucchua, ', ' ORDER BY h.hinhthucchua)
                      FROM lan_chua_hinh_thuc h WHERE h.maluot = l.maluot) AS hinhthucchua,
                   (SELECT string_agg(y.hoten, ', ' ORDER BY y.hoten)
                      FROM ho_tro ht JOIN nhan_vien y ON y.manv = ht.mayt WHERE ht.maluot = l.maluot) AS ytahotro,
                   c.tiendichvu, c.tienthuoc, c.tienthietbi,
                   c.tiendichvu + c.tienthuoc + c.tienthietbi AS tongtien
            FROM luot_kham_chua l
            LEFT JOIN khoa k ON k.makhoa = l.makhoa
            LEFT JOIN nhan_vien nv ON nv.manv = l.mabs
            LEFT JOIN lan_kham lk ON lk.maluot = l.maluot
            LEFT JOIN lan_chua lc ON lc.maluot = l.maluot
            LEFT JOIN chan_doan_dieu_tri cd ON cd.macddt = lc.macddt
            LEFT JOIN lan_kham lkd ON lkd.maluot = cd.maluotkham
            LEFT JOIN benh b ON b.mabenh = cd.mabenh
            LEFT JOIN benh_nhan bn ON bn.mabn = COALESCE(lk.mabn, lkd.mabn)
            CROSS JOIN LATERAL (
                SELECT COALESCE((SELECT sum(d.soluong * d.dongiaapdung) FROM su_dung_dv d WHERE d.maluot = l.maluot), 0) AS tiendichvu,
                       COALESCE((SELECT sum(t.soluong * t.dongiaapdung) FROM ke_don t WHERE t.maluot = l.maluot), 0) AS tienthuoc,
                       COALESCE((SELECT sum(s.solan * s.dongiaapdung) FROM su_dung_tb s WHERE s.maluot = l.maluot), 0) AS tienthietbi
            ) c
            """;

    private final JdbcClient jdbc;

    public LuotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Danh sách lượt theo bộ lọc, mới nhất trước. Mọi tham số có thể null.
     *
     * @param den  mốc kết thúc (không tính), vd đầu ngày hôm sau
     * @param q    tìm theo mã lượt, mã hoặc họ tên bệnh nhân
     */
    public List<LuotResponse> search(LocalDateTime tu, LocalDateTime den, String loai, String maKhoa, String maBS,
                                     String q, int limit) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (tu != null) {
            where.add("l.thoigian >= :tu");
            params.put("tu", tu);
        }
        if (den != null) {
            where.add("l.thoigian < :den");
            params.put("den", den);
        }
        if (loai != null) {
            where.add("l.loailuot = :loai");
            params.put("loai", loai);
        }
        if (maKhoa != null) {
            where.add("l.makhoa = :maKhoa");
            params.put("maKhoa", maKhoa);
        }
        if (maBS != null) {
            where.add("l.mabs = :maBS");
            params.put("maBS", maBS);
        }
        if (q != null) {
            where.add("(l.maluot ILIKE :q OR COALESCE(lk.mabn, lkd.mabn) ILIKE :q OR bn.hoten ILIKE :q)");
            params.put("q", "%" + q + "%");
        }
        String sql = SELECT + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " ORDER BY l.thoigian DESC, l.maluot DESC LIMIT :limit";
        params.put("limit", limit);
        return jdbc.sql(sql).params(params).query(LuotResponse.class).list();
    }

    public Optional<LuotResponse> findById(String maLuot) {
        return jdbc.sql(SELECT + " WHERE l.maluot = :maLuot")
                .param("maLuot", maLuot)
                .query(LuotResponse.class)
                .optional();
    }

    public List<String> findHinhThuc(String maLuot) {
        return jdbc.sql("SELECT hinhthucchua FROM lan_chua_hinh_thuc WHERE maluot = :maLuot ORDER BY hinhthucchua")
                .param("maLuot", maLuot)
                .query(String.class)
                .list();
    }

    public List<YTaHoTroResponse> findYTa(String maLuot) {
        return jdbc.sql("""
                        SELECT ht.mayt, nv.hoten, ht.vaitro
                        FROM ho_tro ht LEFT JOIN nhan_vien nv ON nv.manv = ht.mayt
                        WHERE ht.maluot = :maLuot ORDER BY nv.hoten""")
                .param("maLuot", maLuot)
                .query(YTaHoTroResponse.class)
                .list();
    }

    /** Từng khoản dịch vụ / thuốc / thiết bị của một lượt, đơn giá là đơn giá áp dụng đã lưu (BR7). */
    public List<ChiPhiResponse> findChiPhi(String maLuot) {
        return jdbc.sql("""
                        SELECT d.maluot, 'DICH_VU' AS loai, d.madv AS ma, COALESCE(dv.tendv, d.madv) AS ten,
                               COALESCE(d.ketqua, dv.loaidv) AS ghichu, d.soluong, d.dongiaapdung AS dongia,
                               d.soluong * d.dongiaapdung AS thanhtien
                        FROM su_dung_dv d LEFT JOIN dich_vu dv ON dv.madv = d.madv
                        WHERE d.maluot = :maLuot
                        UNION ALL
                        SELECT k.maluot, 'THUOC', k.mathuoc, COALESCE(t.tenthuoc, k.mathuoc), k.lieudung, k.soluong,
                               k.dongiaapdung, k.soluong * k.dongiaapdung
                        FROM ke_don k LEFT JOIN thuoc t ON t.mathuoc = k.mathuoc
                        WHERE k.maluot = :maLuot
                        UNION ALL
                        SELECT s.maluot, 'THIET_BI', s.matb, COALESCE(tb.tentb, s.matb), tb.loaitb, s.solan,
                               s.dongiaapdung, s.solan * s.dongiaapdung
                        FROM su_dung_tb s LEFT JOIN thiet_bi tb ON tb.matb = s.matb
                        WHERE s.maluot = :maLuot
                        ORDER BY loai, ten""")
                .param("maLuot", maLuot)
                .query(ChiPhiResponse.class)
                .list();
    }

    /** Mã lượt đang có cùng tiền tố (vd K-CN1-TMH-BS-CN1-01-261024-) — để sinh số thứ tự NNN kế tiếp. */
    public List<String> findMaLuotByPrefix(String prefix) {
        return jdbc.sql("SELECT maluot FROM luot_kham_chua WHERE left(maluot, :len) = :prefix")
                .param("len", prefix.length())
                .param("prefix", prefix)
                .query(String.class)
                .list();
    }

    /** Lần chữa có số thứ tự lớn nhất của một đợt (null nếu đợt chưa có lần chữa). */
    public Optional<String> findLanChuaCuoi(String maCDDT) {
        return jdbc.sql("SELECT maluot FROM lan_chua WHERE macddt = :maCDDT ORDER BY lanthu DESC LIMIT 1")
                .param("maCDDT", maCDDT)
                .query(String.class)
                .optional();
    }

    /** Số lần chữa thuộc các đợt mở từ một lần khám — còn lần chữa thì không xoá lần khám được. */
    public int countLanChuaCuaLuotKham(String maLuotKham) {
        return jdbc.sql("""
                        SELECT count(*) FROM lan_chua lc JOIN chan_doan_dieu_tri cd ON cd.macddt = lc.macddt
                        WHERE cd.maluotkham = :maLuot""")
                .param("maLuot", maLuotKham)
                .query(Integer.class)
                .single();
    }

    public void insert(LuotKhamChua luot) {
        jdbc.sql("""
                        INSERT INTO luot_kham_chua (maluot, thoigian, loailuot, ngayhentaikham, ghichu, makhoa, mabs)
                        VALUES (:maLuot, :thoiGian, :loaiLuot, :ngayHenTaiKham, :ghiChu, :maKhoa, :maBS)""")
                .paramSource(luot)
                .update();
    }

    public void insert(LanKham lanKham) {
        jdbc.sql("""
                        INSERT INTO lan_kham (maluot, mabn, lydokham, trieuchung, ketluankham)
                        VALUES (:maLuot, :maBN, :lyDoKham, :trieuChung, :ketLuanKham)""")
                .paramSource(lanKham)
                .update();
    }

    public void insert(LanChua lanChua) {
        jdbc.sql("""
                        INSERT INTO lan_chua (maluot, macddt, lanthu, ketluansauchua, tinhtrangsauchua)
                        VALUES (:maLuot, :maCDDT, :lanThu, :ketLuanSauChua, :tinhTrangSauChua)""")
                .paramSource(lanChua)
                .update();
    }

    public void insert(LanChuaHinhThuc hinhThuc) {
        jdbc.sql("INSERT INTO lan_chua_hinh_thuc (maluot, hinhthucchua) VALUES (:maLuot, :hinhThucChua)")
                .paramSource(hinhThuc)
                .update();
    }

    public void insert(HoTro hoTro) {
        jdbc.sql("INSERT INTO ho_tro (mayt, maluot, vaitro) VALUES (:maYT, :maLuot, :vaiTro)")
                .paramSource(hoTro)
                .update();
    }

    public void insert(SuDungDv suDung) {
        jdbc.sql("""
                        INSERT INTO su_dung_dv (maluot, madv, soluong, dongiaapdung, ketqua)
                        VALUES (:maLuot, :maDV, :soLuong, :donGiaApDung, :ketQua)""")
                .paramSource(suDung)
                .update();
    }

    public void insert(KeDon keDon) {
        jdbc.sql("""
                        INSERT INTO ke_don (maluot, mathuoc, soluong, lieudung, dongiaapdung)
                        VALUES (:maLuot, :maThuoc, :soLuong, :lieuDung, :donGiaApDung)""")
                .paramSource(keDon)
                .update();
    }

    public void insert(SuDungTb suDung) {
        jdbc.sql("""
                        INSERT INTO su_dung_tb (maluot, matb, solan, dongiaapdung)
                        VALUES (:maLuot, :maTB, :soLan, :donGiaApDung)""")
                .paramSource(suDung)
                .update();
    }

    /**
     * Xoá lượt; khoá ngoại ON DELETE CASCADE xoá kèm LAN_KHAM / LAN_CHUA, LAN_CHUA_HINH_THUC, HO_TRO, SU_DUNG_DV,
     * KE_DON, SU_DUNG_TB và các đợt mở từ lần khám. @return số dòng bị xoá
     */
    public int delete(String maLuot) {
        return jdbc.sql("DELETE FROM luot_kham_chua WHERE maluot = :maLuot").param("maLuot", maLuot).update();
    }
}
