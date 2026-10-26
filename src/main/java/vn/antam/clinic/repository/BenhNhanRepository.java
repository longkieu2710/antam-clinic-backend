package vn.antam.clinic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.BenhNhanResponse;
import vn.antam.clinic.dto.response.BenhNhanToanChuoiResponse;
import vn.antam.clinic.entity.BenhNhan;
import vn.antam.clinic.entity.ChanDoanDieuTri;

/**
 * Bảng BENH_NHAN của chi nhánh đang chọn, và view TRA_CUU_BENH_NHAN_TOAN_CHUOI (postgres_fdw về S0)
 * để tra cứu / sinh mã bệnh nhân duy nhất toàn chuỗi.
 */
@Repository
public class BenhNhanRepository {

    private static final String SELECT = """
            SELECT bn.mabn, bn.hoten, bn.ngaysinh, bn.gioitinh, bn.cccd, bn.sodienthoai, bn.diachi,
                   bn.sothebhyt, bn.nhommau, bn.tiensudiung,
                   (SELECT count(*) FROM lan_kham lk WHERE lk.mabn = bn.mabn) AS solankham,
                   (SELECT max(l.thoigian) FROM lan_kham lk JOIN luot_kham_chua l ON l.maluot = lk.maluot
                     WHERE lk.mabn = bn.mabn) AS lankhamgannhat,
                   (SELECT count(*) FROM chan_doan_dieu_tri cd JOIN lan_kham lk ON lk.maluot = cd.maluotkham
                     WHERE lk.mabn = bn.mabn AND cd.trangthai = :dangDieuTri) AS sodotdangdieutri
            FROM benh_nhan bn
            """;

    private final JdbcClient jdbc;

    public BenhNhanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param q tìm theo mã, họ tên, CCCD, số điện thoại hoặc số thẻ BHYT; bỏ trống = tất cả */
    public List<BenhNhanResponse> search(String q) {
        if (q == null || q.isBlank()) {
            return jdbc.sql(SELECT + " ORDER BY bn.mabn")
                    .param("dangDieuTri", ChanDoanDieuTri.DANG_DIEU_TRI)
                    .query(BenhNhanResponse.class)
                    .list();
        }
        return jdbc.sql(SELECT + """
                         WHERE bn.mabn ILIKE :q OR bn.hoten ILIKE :q OR bn.cccd ILIKE :q
                            OR bn.sodienthoai ILIKE :q OR bn.sothebhyt ILIKE :q
                         ORDER BY bn.mabn""")
                .param("dangDieuTri", ChanDoanDieuTri.DANG_DIEU_TRI)
                .param("q", "%" + q.trim() + "%")
                .query(BenhNhanResponse.class)
                .list();
    }

    public Optional<BenhNhanResponse> findById(String maBN) {
        return jdbc.sql(SELECT + " WHERE bn.mabn = :maBN")
                .param("dangDieuTri", ChanDoanDieuTri.DANG_DIEU_TRI)
                .param("maBN", maBN)
                .query(BenhNhanResponse.class)
                .optional();
    }

    public boolean exists(String maBN) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM benh_nhan WHERE mabn = :maBN)")
                .param("maBN", maBN)
                .query(Boolean.class)
                .single();
    }

    public void insert(BenhNhan bn) {
        jdbc.sql("""
                        INSERT INTO benh_nhan (mabn, hoten, ngaysinh, gioitinh, cccd, sodienthoai, diachi,
                                               sothebhyt, nhommau, tiensudiung)
                        VALUES (:maBN, :hoTen, :ngaySinh, :gioiTinh, :cccd, :soDienThoai, :diaChi,
                                :soTheBHYT, :nhomMau, :tienSuDiUng)""")
                .paramSource(bn)
                .update();
    }

    /** @return số dòng được sửa (0 = không có bệnh nhân này ở chi nhánh) */
    public int update(BenhNhan bn) {
        return jdbc.sql("""
                        UPDATE benh_nhan
                           SET hoten = :hoTen, ngaysinh = :ngaySinh, gioitinh = :gioiTinh, cccd = :cccd,
                               sodienthoai = :soDienThoai, diachi = :diaChi, sothebhyt = :soTheBHYT,
                               nhommau = :nhomMau, tiensudiung = :tienSuDiUng
                         WHERE mabn = :maBN""")
                .paramSource(bn)
                .update();
    }

    /** @return số dòng bị xoá */
    public int delete(String maBN) {
        return jdbc.sql("DELETE FROM benh_nhan WHERE mabn = :maBN").param("maBN", maBN).update();
    }

    /** Mã bệnh nhân của cả chuỗi (để sinh mã mới không trùng). Lỗi nếu S0 hoặc một chi nhánh đang tắt. */
    public List<String> findAllMaBNToanChuoi() {
        return jdbc.sql("SELECT mabn FROM tra_cuu_benh_nhan_toan_chuoi").query(String.class).list();
    }

    /**
     * Tra cứu toàn chuỗi khi tiếp đón: khớp đúng CCCD / thẻ BHYT / số điện thoại / mã, hoặc một phần họ tên.
     * Lỗi nếu S0 hoặc một chi nhánh đang tắt.
     */
    public List<BenhNhanToanChuoiResponse> searchToanChuoi(String q) {
        return jdbc.sql("""
                        SELECT t.mabn, t.hoten, t.ngaysinh, t.gioitinh, t.cccd, t.sodienthoai, t.sothebhyt, t.diachi,
                               EXISTS (SELECT 1 FROM benh_nhan b WHERE b.mabn = t.mabn) AS cotaichinhanh,
                               (SELECT count(*) FROM chan_doan_dieu_tri cd JOIN lan_kham lk ON lk.maluot = cd.maluotkham
                                 WHERE lk.mabn = t.mabn AND cd.trangthai = :dangDieuTri) AS sodotdangdieutri
                        FROM tra_cuu_benh_nhan_toan_chuoi t
                        WHERE t.cccd = :q OR t.sothebhyt = :q OR t.sodienthoai = :q OR t.mabn = :q
                           OR t.hoten ILIKE :like
                        ORDER BY t.mabn
                        LIMIT 20""")
                .param("q", q.trim())
                .param("like", "%" + q.trim() + "%")
                .param("dangDieuTri", ChanDoanDieuTri.DANG_DIEU_TRI)
                .query(BenhNhanToanChuoiResponse.class)
                .list();
    }
}
