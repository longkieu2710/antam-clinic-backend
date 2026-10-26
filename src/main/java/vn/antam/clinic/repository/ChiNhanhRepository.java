package vn.antam.clinic.repository;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.entity.ChiNhanh;

/** Bảng CHI_NHANH tại S0 (Logical Replication đẩy xuống S1..S3). */
@Repository
public class ChiNhanhRepository {

    private final JdbcClient jdbc;

    public ChiNhanhRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChiNhanh> findAll() {
        return jdbc.sql("SELECT macn, tencn, diachi, sodienthoai FROM chi_nhanh ORDER BY macn")
                .query(ChiNhanh.class)
                .list();
    }

    /** @return số dòng được sửa (0 = không có chi nhánh này) */
    public int update(ChiNhanh cn) {
        return jdbc.sql("UPDATE chi_nhanh SET tencn = :tenCn, diachi = :diaChi, sodienthoai = :soDienThoai WHERE macn = :maCn")
                .paramSource(cn)
                .update();
    }
}
