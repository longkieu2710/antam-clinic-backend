package vn.antam.clinic.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.BacSiRequest;
import vn.antam.clinic.dto.response.BacSiResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.entity.BacSi;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BacSiRepository;
import vn.antam.clinic.repository.NhanVienRepository;
import vn.antam.clinic.site.SiteContext;
import vn.antam.clinic.util.Codes;

/**
 * Nghiệp vụ bác sĩ. Mỗi thao tác ghi là MỘT transaction trên trạm chi nhánh đang chọn:
 * NHAN_VIEN (cha, ISA) + BAC_SI (con) + NV_SODIENTHOAI (thuộc tính đa trị).
 */
@Service
public class BacSiService {

    private static final String DANG_LAM_VIEC = "Đang làm việc";

    private final NhanVienRepository nhanVienRepo;
    private final BacSiRepository bacSiRepo;
    private final AppProperties props;

    public BacSiService(NhanVienRepository nhanVienRepo, BacSiRepository bacSiRepo, AppProperties props) {
        this.nhanVienRepo = nhanVienRepo;
        this.bacSiRepo = bacSiRepo;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<BacSiResponse> search(String q, String maKhoa) {
        return bacSiRepo.search(q, maKhoa);
    }

    @Transactional(readOnly = true)
    public BacSiResponse get(String maNV) {
        return bacSiRepo.findById(maNV).orElseThrow(() -> BusinessException.notFound("bác sĩ", maNV));
    }

    @Transactional
    public BacSiResponse create(BacSiRequest input) {
        BacSiRequest r = normalize(input);
        String maNV = r.maNV() != null ? r.maNV() : nextMaNV();
        LocalDate ngayVaoLam = r.ngayVaoLam() != null ? r.ngayVaoLam() : LocalDate.now();
        nhanVienRepo.insert(toNhanVien(maNV, r, ngayVaoLam));
        bacSiRepo.insert(toBacSi(maNV, r));
        nhanVienRepo.replacePhones(maNV, r.soDienThoai());
        return get(maNV);
    }

    @Transactional
    public BacSiResponse update(String maNV, BacSiRequest input) {
        BacSiRequest r = normalize(input);
        if (nhanVienRepo.update(toNhanVien(maNV, r, r.ngayVaoLam())) == 0) {
            throw BusinessException.notFound("bác sĩ", maNV);
        }
        bacSiRepo.update(toBacSi(maNV, r));
        nhanVienRepo.replacePhones(maNV, r.soDienThoai());
        return get(maNV);
    }

    /**
     * BR9: bác sĩ đã có lượt khám/chữa thì không xoá (giữ lịch sử, bảng lương) mà chuyển "Ngừng hoạt động".
     */
    @Transactional
    public DeleteResponse delete(String maNV) {
        BacSiResponse bs = get(maNV);
        long soLuot = bacSiRepo.countLuot(maNV);
        if (soLuot > 0) {
            nhanVienRepo.markInactive(maNV);
            return DeleteResponse.soft(bs.hoTen() + " đã có " + soLuot
                    + " lượt khám/chữa nên không xoá — đã chuyển trạng thái \"Ngừng hoạt động\".");
        }
        bacSiRepo.delete(maNV);
        nhanVienRepo.delete(maNV);
        return DeleteResponse.hard("Đã xoá bác sĩ " + bs.hoTen() + " (" + maNV + ").");
    }

    /** Mã kế tiếp dạng BS-{MaCN}-nn theo chi nhánh đang chọn (X-Site). */
    private String nextMaNV() {
        String site = SiteContext.get();
        String maCn = props.site(site).map(AppProperties.Site::maCn).orElse(site);
        return Codes.next("BS-" + maCn + "-", nhanVienRepo.findAllMaNV(), 2);
    }

    private static NhanVien toNhanVien(String maNV, BacSiRequest r, LocalDate ngayVaoLam) {
        return new NhanVien(maNV, r.hoTen().trim(), r.ngaySinh(), r.gioiTinh(), r.cccd(), r.diaChi(), r.heSoLuong(),
                ngayVaoLam, r.trangThai() != null ? r.trangThai() : DANG_LAM_VIEC, NhanVien.LOAI_BAC_SI, r.maKhoa());
    }

    private static BacSi toBacSi(String maNV, BacSiRequest r) {
        return new BacSi(maNV, r.chuyenKhoa(), r.hocVi(), r.soChungChiHanhNghe());
    }

    /** Chuẩn hoá: chuỗi rỗng → null (tránh trùng UNIQUE với ''), bỏ số điện thoại trống / trùng. */
    private static BacSiRequest normalize(BacSiRequest r) {
        List<String> phones = r.soDienThoai() == null ? List.of()
                : r.soDienThoai().stream().map(Codes::blankToNull).filter(Objects::nonNull).distinct().toList();
        return new BacSiRequest(
                Codes.blankToNull(r.maNV()),
                r.hoTen(),
                r.ngaySinh(),
                Codes.blankToNull(r.gioiTinh()),
                Codes.blankToNull(r.cccd()),
                Codes.blankToNull(r.diaChi()),
                r.heSoLuong(),
                r.ngayVaoLam(),
                Codes.blankToNull(r.trangThai()),
                r.maKhoa(),
                Codes.blankToNull(r.chuyenKhoa()),
                Codes.blankToNull(r.hocVi()),
                Codes.blankToNull(r.soChungChiHanhNghe()),
                phones);
    }
}
