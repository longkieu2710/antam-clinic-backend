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

    /**
     * Gom số liệu bằng CTE GROUP BY (không truy vấn con theo từng dòng): câu này còn chạy ở S0 trên view toàn cục qua
     * postgres_fdw (/api/hq/**), mỗi bảng chỉ quét qua FDW một lần.
     */
    private static final String SELECT = """
            WITH nv AS (SELECT makhoa, count(*) AS sonhanvien FROM nhan_vien GROUP BY makhoa),
                 luot AS (SELECT makhoa, count(*) AS soluot FROM luot_kham_chua GROUP BY makhoa)
            SELECT k.makhoa, k.tenkhoa, k.mota, k.macn,
                   COALESCE(nv.sonhanvien, 0) AS sonhanvien, COALESCE(luot.soluot, 0) AS soluot
            FROM khoa k
            LEFT JOIN nv ON nv.makhoa = k.makhoa
            LEFT JOIN luot ON luot.makhoa = k.makhoa
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
