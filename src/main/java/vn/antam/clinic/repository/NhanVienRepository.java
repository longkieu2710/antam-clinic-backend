package vn.antam.clinic.repository;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.antam.clinic.entity.NhanVien;

/** Bảng NHAN_VIEN và NV_SODIENTHOAI (thuộc tính đa trị) — dùng chung cho bác sĩ và y tá. */
@Repository
public class NhanVienRepository {

    private final JdbcClient jdbc;

    public NhanVienRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> findAllMaNV() {
        return jdbc.sql("SELECT manv FROM nhan_vien").query(String.class).list();
    }

    public void insert(NhanVien nv) {
        jdbc.sql("""
                        INSERT INTO nhan_vien (manv, hoten, ngaysinh, gioitinh, cccd, diachi, hesoluong,
                                               ngayvaolam, trangthai, loainv, makhoa)
                        VALUES (:maNV, :hoTen, :ngaySinh, :gioiTinh, :cccd, :diaChi, :heSoLuong,
                                :ngayVaoLam, :trangThai, :loaiNV, :maKhoa)""")
                .paramSource(nv)
                .update();
    }

    /**
     * Sửa nhân viên đúng loại {@code nv.loaiNV()} — không đổi bác sĩ thành y tá hay ngược lại.
     *
     * @return số dòng được sửa (0 = không có nhân viên loại này với mã đó)
     */
    public int update(NhanVien nv) {
        return jdbc.sql("""
                        UPDATE nhan_vien
                           SET hoten = :hoTen, ngaysinh = :ngaySinh, gioitinh = :gioiTinh, cccd = :cccd,
                               diachi = :diaChi, hesoluong = :heSoLuong, ngayvaolam = :ngayVaoLam,
                               trangthai = :trangThai, makhoa = :maKhoa
                         WHERE manv = :maNV AND loainv = :loaiNV""")
                .paramSource(nv)
                .update();
    }

    /** Ghi lại toàn bộ số điện thoại (thuộc tính đa trị → bảng NV_SODIENTHOAI). */
    public void replacePhones(String maNV, List<String> phones) {
        jdbc.sql("DELETE FROM nv_sodienthoai WHERE manv = :maNV").param("maNV", maNV).update();
        for (String phone : phones) {
            jdbc.sql("INSERT INTO nv_sodienthoai (manv, sodienthoai) VALUES (:maNV, :sdt)")
                    .param("maNV", maNV)
                    .param("sdt", phone)
                    .update();
        }
    }

    public void markInactive(String maNV) {
        jdbc.sql("UPDATE nhan_vien SET trangthai = 'Ngừng hoạt động' WHERE manv = :maNV")
                .param("maNV", maNV)
                .update();
    }

    /**
     * Xoá số điện thoại rồi xoá nhân viên. Bảng con chuyên biệt (BAC_SI, Y_TA) phải được xoá trước
     * để chạy được cả khi khoá ngoại không có ON DELETE CASCADE.
     */
    public void delete(String maNV) {
        jdbc.sql("DELETE FROM nv_sodienthoai WHERE manv = :maNV").param("maNV", maNV).update();
        jdbc.sql("DELETE FROM nhan_vien WHERE manv = :maNV").param("maNV", maNV).update();
    }
}
