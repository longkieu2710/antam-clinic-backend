package vn.antam.clinic.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.YTaRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.YTaResponse;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.entity.YTa;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.NhanVienRepository;
import vn.antam.clinic.repository.YTaRepository;
import vn.antam.clinic.site.SiteContext;
import vn.antam.clinic.util.Codes;

/**
 * Nghiệp vụ y tá. Mỗi thao tác ghi là MỘT transaction trên trạm chi nhánh đang chọn:
 * NHAN_VIEN (cha, ISA) + Y_TA (con) + NV_SODIENTHOAI (thuộc tính đa trị).
 */
@Service
public class YTaService {

    private static final String DANG_LAM_VIEC = "Đang làm việc";

    private final NhanVienRepository nhanVienRepo;
    private final YTaRepository yTaRepo;
    private final AppProperties props;

    public YTaService(NhanVienRepository nhanVienRepo, YTaRepository yTaRepo, AppProperties props) {
        this.nhanVienRepo = nhanVienRepo;
        this.yTaRepo = yTaRepo;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<YTaResponse> search(String q, String maKhoa) {
        return yTaRepo.search(q, maKhoa);
    }

    @Transactional(readOnly = true)
    public YTaResponse get(String maNV) {
        return yTaRepo.findById(maNV).orElseThrow(() -> BusinessException.notFound("y tá", maNV));
    }

    @Transactional
    public YTaResponse create(YTaRequest input) {
        YTaRequest r = normalize(input);
        String maNV = r.maNV() != null ? r.maNV() : nextMaNV();
        LocalDate ngayVaoLam = r.ngayVaoLam() != null ? r.ngayVaoLam() : LocalDate.now();
        nhanVienRepo.insert(toNhanVien(maNV, r, ngayVaoLam));
        yTaRepo.insert(toYTa(maNV, r));
        nhanVienRepo.replacePhones(maNV, r.soDienThoai());
        return get(maNV);
    }

    @Transactional
    public YTaResponse update(String maNV, YTaRequest input) {
        YTaRequest r = normalize(input);
        if (nhanVienRepo.update(toNhanVien(maNV, r, r.ngayVaoLam())) == 0) {
            throw BusinessException.notFound("y tá", maNV);
        }
        yTaRepo.update(toYTa(maNV, r));
        nhanVienRepo.replacePhones(maNV, r.soDienThoai());
        return get(maNV);
    }

    /** BR9: y tá đã hỗ trợ lượt khám/chữa (dữ liệu tính lương) thì không xoá mà chuyển "Ngừng hoạt động". */
    @Transactional
    public DeleteResponse delete(String maNV) {
        YTaResponse yt = get(maNV);
        long soLanHoTro = yTaRepo.countHoTro(maNV);
        if (soLanHoTro > 0) {
            nhanVienRepo.markInactive(maNV);
            return DeleteResponse.soft(yt.hoTen() + " đã hỗ trợ " + soLanHoTro
                    + " lượt khám/chữa nên không xoá — đã chuyển trạng thái \"Ngừng hoạt động\".");
        }
        yTaRepo.delete(maNV);
        nhanVienRepo.delete(maNV);
        return DeleteResponse.hard("Đã xoá y tá " + yt.hoTen() + " (" + maNV + ").");
    }

    /** Mã kế tiếp dạng YT-{MaCN}-nn theo chi nhánh đang chọn (X-Site). */
    private String nextMaNV() {
        String site = SiteContext.get();
        String maCn = props.site(site).map(AppProperties.Site::maCn).orElse(site);
        return Codes.next("YT-" + maCn + "-", nhanVienRepo.findAllMaNV(), 2);
    }

    private static NhanVien toNhanVien(String maNV, YTaRequest r, LocalDate ngayVaoLam) {
        return new NhanVien(maNV, r.hoTen().trim(), r.ngaySinh(), r.gioiTinh(), r.cccd(), r.diaChi(), r.heSoLuong(),
                ngayVaoLam, r.trangThai() != null ? r.trangThai() : DANG_LAM_VIEC, NhanVien.LOAI_Y_TA, r.maKhoa());
    }

    private static YTa toYTa(String maNV, YTaRequest r) {
        return new YTa(maNV, r.trinhDo(), r.soChungChiHanhNghe());
    }

    /** Chuẩn hoá: chuỗi rỗng → null (tránh trùng UNIQUE với ''), bỏ số điện thoại trống / trùng. */
    private static YTaRequest normalize(YTaRequest r) {
        List<String> phones = r.soDienThoai() == null ? List.of()
                : r.soDienThoai().stream().map(Codes::blankToNull).filter(Objects::nonNull).distinct().toList();
        return new YTaRequest(
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
                Codes.blankToNull(r.trinhDo()),
                Codes.blankToNull(r.soChungChiHanhNghe()),
                phones);
    }
}
