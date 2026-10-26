package vn.antam.clinic.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.ThongKeBenhItemResponse;

/** Truy vấn thống kê bệnh (F2) trên chi nhánh đang chọn. */
@Repository
public class ThongKeBenhRepository {

    private final JdbcClient jdbc;

    public ThongKeBenhRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Mỗi đợt điều trị (CHAN_DOAN_DIEU_TRI) được tính 1 lần nếu lần khám mở đợt HOẶC một lần chữa của đợt nằm trong
     * khoảng [tu, den). UNION khử trùng lặp nên nhiều lượt liên tiếp của cùng một đợt vẫn chỉ là 1 lần mắc;
     * mắc lại sau khi khỏi là đợt mới nên được tính thêm.
     */
    public List<ThongKeBenhItemResponse> thongKe(LocalDateTime tu, LocalDateTime den) {
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
                        SELECT b.mabenh, b.tenbenh, b.maicd10,
                               count(*) AS solanmac,
                               count(DISTINCT lk.mabn) AS sobenhnhan
                        FROM dot d
                        JOIN chan_doan_dieu_tri cd ON cd.macddt = d.macddt
                        JOIN lan_kham lk ON lk.maluot = cd.maluotkham
                        JOIN benh b ON b.mabenh = cd.mabenh
                        GROUP BY b.mabenh, b.tenbenh, b.maicd10
                        ORDER BY solanmac DESC, sobenhnhan DESC, b.tenbenh""")
                .param("tu", tu)
                .param("den", den)
                .query(ThongKeBenhItemResponse.class)
                .list();
    }
}
