package vn.antam.clinic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.KhoaResponse;
import vn.antam.clinic.entity.Khoa;

/** Bảng KHOA của chi nhánh đang chọn. */
@Repository
public class KhoaRepository {

    private static final String SELECT = """
            SELECT k.makhoa, k.tenkhoa, k.mota, k.macn,
                   (SELECT count(*) FROM nhan_vien nv WHERE nv.makhoa = k.makhoa) AS sonhanvien,
                   (SELECT count(*) FROM luot_kham_chua l WHERE l.makhoa = k.makhoa) AS soluot
            FROM khoa k
            """;

    private final JdbcClient jdbc;

    public KhoaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Danh sách khoa kèm số nhân viên, số lượt — truy vấn tổng hợp nên trả thẳng response (projection). */
    public List<KhoaResponse> findAllWithSoNhanVien() {
        return jdbc.sql(SELECT + " ORDER BY k.makhoa").query(KhoaResponse.class).list();
    }

    public Optional<KhoaResponse> findById(String maKhoa) {
        return jdbc.sql(SELECT + " WHERE k.makhoa = :maKhoa")
                .param("maKhoa", maKhoa)
                .query(KhoaResponse.class)
                .optional();
    }

    public List<String> findAllMaKhoa() {
        return jdbc.sql("SELECT makhoa FROM khoa").query(String.class).list();
    }

    public void insert(Khoa khoa) {
        jdbc.sql("INSERT INTO khoa (makhoa, tenkhoa, mota, macn) VALUES (:maKhoa, :tenKhoa, :moTa, :maCn)")
                .paramSource(khoa)
                .update();
    }

    /** Chỉ sửa tên và mô tả — không đổi mã, không chuyển khoa sang chi nhánh khác. */
    public int update(Khoa khoa) {
        return jdbc.sql("UPDATE khoa SET tenkhoa = :tenKhoa, mota = :moTa WHERE makhoa = :maKhoa")
                .paramSource(khoa)
                .update();
    }

    public int delete(String maKhoa) {
        return jdbc.sql("DELETE FROM khoa WHERE makhoa = :maKhoa").param("maKhoa", maKhoa).update();
    }
}
