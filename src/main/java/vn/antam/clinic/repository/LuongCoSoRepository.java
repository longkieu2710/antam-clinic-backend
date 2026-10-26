package vn.antam.clinic.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.entity.LuongCoSo;

/** Bảng LUONG_CO_SO — bản gốc khi chạy ở S0, bản sao khi chạy ở chi nhánh (dùng để tính lương). */
@Repository
public class LuongCoSoRepository {

    private final JdbcClient jdbc;

    public LuongCoSoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Mới nhất trước. */
    public List<LuongCoSo> findAll() {
        return jdbc.sql("SELECT ngayapdung, mucluong, vanbanquydinh FROM luong_co_so ORDER BY ngayapdung DESC")
                .query(LuongCoSo.class)
                .list();
    }

    public Optional<LuongCoSo> findById(LocalDate ngayApDung) {
        return jdbc.sql("SELECT ngayapdung, mucluong, vanbanquydinh FROM luong_co_so WHERE ngayapdung = :ngay")
                .param("ngay", ngayApDung)
                .query(LuongCoSo.class)
                .optional();
    }

    /** Mức có hiệu lực tại ngày {@code ngay}: ngày áp dụng gần nhất ≤ {@code ngay}. */
    public Optional<LuongCoSo> findApDungTai(LocalDate ngay) {
        return jdbc.sql("""
                        SELECT ngayapdung, mucluong, vanbanquydinh FROM luong_co_so
                        WHERE ngayapdung <= :ngay ORDER BY ngayapdung DESC LIMIT 1""")
                .param("ngay", ngay)
                .query(LuongCoSo.class)
                .optional();
    }

    public void insert(LuongCoSo row) {
        jdbc.sql("""
                        INSERT INTO luong_co_so (ngayapdung, mucluong, vanbanquydinh)
                        VALUES (:ngayApDung, :mucLuong, :vanBanQuyDinh)""")
                .paramSource(row)
                .update();
    }

    public int update(LuongCoSo row) {
        return jdbc.sql("UPDATE luong_co_so SET mucluong = :mucLuong, vanbanquydinh = :vanBanQuyDinh WHERE ngayapdung = :ngayApDung")
                .paramSource(row)
                .update();
    }

    public int delete(LocalDate ngayApDung) {
        return jdbc.sql("DELETE FROM luong_co_so WHERE ngayapdung = :ngay").param("ngay", ngayApDung).update();
    }
}
