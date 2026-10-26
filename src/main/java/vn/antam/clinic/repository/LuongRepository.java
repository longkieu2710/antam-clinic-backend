package vn.antam.clinic.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.dto.response.LuongNhanVienResponse;
import vn.antam.clinic.entity.ChanDoanDieuTri;

/** Truy vấn tính lương (F3) trên chi nhánh đang chọn — toàn bộ công thức nằm trong SQL. */
@Repository
public class LuongRepository {

    private static final String NGUNG_HOAT_DONG = "Ngừng hoạt động";

    private final JdbcClient jdbc;

    public LuongRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lương = round(HeSoLuong × mức cơ sở) + thưởng.
     * Bác sĩ: + thuongDot × số đợt "Đã khỏi" có NgayKetThuc trong [tu, den), đợt thuộc bác sĩ của lần khám mở đợt
     * (BR2: một bệnh nhân – một bệnh – một bác sĩ); đợt "Ngừng điều trị" không được tính.
     * Y tá: + thuongHoTro × số dòng HO_TRO của các lượt trong [tu, den) (mỗi cặp y tá – lượt tính 1 lần).
     * Nhân viên đã vào làm trước cuối tháng; người "Ngừng hoạt động" chỉ hiện khi vẫn có thưởng trong tháng.
     */
    public List<LuongNhanVienResponse> tinhLuong(LocalDateTime tu, LocalDateTime den, BigDecimal mucLuongCoSo,
                                                 BigDecimal thuongDot, BigDecimal thuongHoTro) {
        return jdbc.sql("""
                        SELECT x.manv, x.hoten, x.loainv, x.makhoa, x.tenkhoa, x.trangthai, x.hesoluong,
                               round(x.hesoluong * :mucLuong, 0) AS luongcoban,
                               x.sodotkhoi, x.solanhotro,
                               x.sodotkhoi * :thuongDot + x.solanhotro * :thuongHoTro AS thuong,
                               round(x.hesoluong * :mucLuong, 0)
                                   + x.sodotkhoi * :thuongDot + x.solanhotro * :thuongHoTro AS tongluong
                        FROM (
                            SELECT nv.manv, nv.hoten, nv.loainv, nv.makhoa, k.tenkhoa, nv.trangthai, nv.hesoluong,
                                   (SELECT count(*)
                                      FROM chan_doan_dieu_tri cd
                                      JOIN luot_kham_chua l ON l.maluot = cd.maluotkham
                                     WHERE l.mabs = nv.manv AND cd.trangthai = :daKhoi
                                       AND cd.ngayketthuc >= :tu AND cd.ngayketthuc < :den) AS sodotkhoi,
                                   (SELECT count(*)
                                      FROM ho_tro h
                                      JOIN luot_kham_chua l ON l.maluot = h.maluot
                                     WHERE h.mayt = nv.manv AND l.thoigian >= :tu AND l.thoigian < :den) AS solanhotro
                            FROM nhan_vien nv
                            LEFT JOIN khoa k ON k.makhoa = nv.makhoa
                            WHERE nv.ngayvaolam IS NULL OR nv.ngayvaolam < :denNgay
                        ) x
                        WHERE x.trangthai IS DISTINCT FROM :ngung OR x.sodotkhoi > 0 OR x.solanhotro > 0
                        ORDER BY x.loainv, x.manv""")
                .param("tu", tu)
                .param("den", den)
                .param("denNgay", den.toLocalDate())
                .param("mucLuong", mucLuongCoSo)
                .param("thuongDot", thuongDot)
                .param("thuongHoTro", thuongHoTro)
                .param("daKhoi", ChanDoanDieuTri.DA_KHOI)
                .param("ngung", NGUNG_HOAT_DONG)
                .query(LuongNhanVienResponse.class)
                .list();
    }
}
