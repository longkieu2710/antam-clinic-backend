package vn.antam.clinic.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.antam.clinic.dto.request.LuotChuaRequest;
import vn.antam.clinic.dto.request.NgungDieuTriRequest;
import vn.antam.clinic.dto.response.BacSiResponse;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.dto.response.TaoLuotResponse;
import vn.antam.clinic.entity.ChanDoanDieuTri;
import vn.antam.clinic.entity.LanChua;
import vn.antam.clinic.entity.LanChuaHinhThuc;
import vn.antam.clinic.entity.LuotKhamChua;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BacSiRepository;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.LuotRepository;
import vn.antam.clinic.util.Codes;

/**
 * Tạo lượt chữa cho một đợt đang điều trị, và đánh dấu đợt "Ngừng điều trị".
 * Chi nhánh không có trigger nên BR2, BR3, BR10, BR11 được kiểm tra tại đây, trong cùng transaction với phần ghi.
 */
@Service
public class LuotChuaService {

    private final LuotRepository luotRepo;
    private final ChanDoanDieuTriRepository dotRepo;
    private final BacSiRepository bacSiRepo;
    private final ChiTietLuotService chiTietService;
    private final TransactionTemplate tx;

    public LuotChuaService(LuotRepository luotRepo, ChanDoanDieuTriRepository dotRepo, BacSiRepository bacSiRepo,
                           ChiTietLuotService chiTietService, PlatformTransactionManager transactionManager) {
        this.luotRepo = luotRepo;
        this.dotRepo = dotRepo;
        this.bacSiRepo = bacSiRepo;
        this.chiTietService = chiTietService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Các đợt "Đang điều trị" của bệnh nhân tại chi nhánh — chỉ những đợt này mới thêm được lần chữa (BR3). */
    @Transactional(readOnly = true)
    public List<DotDieuTriResponse> dotDangDieuTri(String maBN) {
        return dotRepo.findDangDieuTri(maBN);
    }

    /** Phần ghi chạy trong {@link TransactionTemplate} để trùng mã vừa sinh (lưu đồng thời) thì thử lại transaction mới. */
    public TaoLuotResponse create(LuotChuaRequest r) {
        LocalDateTime thoiGian = r.thoiGian() != null ? r.thoiGian().truncatedTo(ChronoUnit.SECONDS)
                : LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (thoiGian.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FUTURE_TIME", "Thời gian chữa không được ở tương lai.");
        }
        if (!LanChua.TINH_TRANG.contains(r.tinhTrangSauChua())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Tình trạng sau chữa là một trong: " + String.join(", ", LanChua.TINH_TRANG) + ".");
        }
        boolean khoi = LanChua.KHOI.equals(r.tinhTrangSauChua());
        if (!khoi && r.ngayHenTaiKham() != null && !r.ngayHenTaiKham().isAfter(thoiGian.toLocalDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_APPOINTMENT", "Ngày hẹn tái khám phải sau ngày chữa.");
        }
        Set<String> hinhThuc = new LinkedHashSet<>();
        r.hinhThuc().forEach(h -> hinhThuc.add(h.trim()));

        for (int lan = 1; ; lan++) {
            try {
                return tx.execute(status -> ghi(r, thoiGian, khoi, hinhThuc));
            } catch (DuplicateKeyException e) {
                if (lan >= LuotKhamService.SO_LAN_THU) {
                    throw e;
                }
            }
        }
    }

    private TaoLuotResponse ghi(LuotChuaRequest r, LocalDateTime thoiGian, boolean khoi, Set<String> hinhThuc) {
        DotDieuTriResponse dot = dotRepo.findById(r.maCDDT())
                .orElseThrow(() -> BusinessException.notFound("đợt điều trị (tại chi nhánh này)", r.maCDDT()));
        // BR3: chỉ thêm lần chữa vào đợt đang điều trị, sau lượt gần nhất của đợt
        if (!ChanDoanDieuTri.DANG_DIEU_TRI.equals(dot.trangThai())) {
            throw new BusinessException(HttpStatus.CONFLICT, "BR3", "Đợt \"" + dot.tenBenh() + "\" đã kết thúc ("
                    + dot.trangThai() + ") — không thêm lần chữa được. Nếu bệnh nhân mắc lại, tạo lượt khám mới (BR3).");
        }
        if (!thoiGian.isAfter(dot.luotGanNhat())) {
            throw ChiTietLuotService.rule("BR3", "Thời gian lần chữa phải sau lượt gần nhất của đợt ("
                    + dot.luotGanNhat().toString().replace('T', ' ') + ") (BR3).");
        }
        // BR2: bác sĩ, khoa cố định theo lần khám mở đợt
        BacSiResponse bs = bacSiRepo.findById(dot.maBS()).orElseThrow(() -> BusinessException.notFound("bác sĩ", dot.maBS()));
        if (NhanVien.NGUNG_HOAT_DONG.equals(bs.trangThai())) {
            throw ChiTietLuotService.rule("BR2", "Bác sĩ phụ trách đợt (" + bs.hoTen() + ") đã ngừng hoạt động — mọi lần "
                    + "chữa của đợt phải do bác sĩ đã khám mở đợt thực hiện (BR2). Cân nhắc chuyển đợt \"Ngừng điều trị\".");
        }

        ChiTietLuotService.ChiTiet chiTiet = chiTietService.kiemTra(r, true);

        int lanThu = dotRepo.countLanChua(dot.maCDDT()) + 1;
        String prefix = Codes.luotPrefix("C", dot.maKhoa(), dot.maBS(), thoiGian);
        String maLuot = Codes.next(prefix, luotRepo.findMaLuotByPrefix(prefix), 3);

        luotRepo.insert(new LuotKhamChua(maLuot, thoiGian, LuotKhamChua.LOAI_CHUA, khoi ? null : r.ngayHenTaiKham(),
                Codes.blankToNull(r.ghiChu()), dot.maKhoa(), dot.maBS()));
        luotRepo.insert(new LanChua(maLuot, dot.maCDDT(), lanThu, Codes.blankToNull(r.ketLuanSauChua()), r.tinhTrangSauChua()));
        hinhThuc.forEach(h -> luotRepo.insert(new LanChuaHinhThuc(maLuot, h)));
        ChiTietLuotService.ChiTiet rows = chiTiet.withMaLuot(maLuot);
        chiTietService.ghi(rows);

        // BR10: kết luận "Khỏi" → đợt kết thúc "Đã khỏi" (bác sĩ được thưởng ở F3); bác sĩ được chỉnh số lần dự kiến
        int soLan = r.soLanChuaCanThiet() != null ? r.soLanChuaCanThiet()
                : dot.soLanChuaCanThiet() != null ? dot.soLanChuaCanThiet() : 1;
        dotRepo.updateTienTrinh(dot.maCDDT(), soLan, khoi ? ChanDoanDieuTri.DA_KHOI : ChanDoanDieuTri.DANG_DIEU_TRI,
                khoi ? thoiGian : null);

        return new TaoLuotResponse(maLuot, LuotKhamChua.LOAI_CHUA, dot.maBN(), dot.tenBN(), List.of(dot.maCDDT()),
                lanThu, khoi, false, rows.tongTien(), List.of());
    }

    /** Đánh dấu đợt "Ngừng điều trị" — bệnh nhân bỏ dở (BR12); bác sĩ không được thưởng cho đợt này. */
    @Transactional
    public DotDieuTriResponse ngungDieuTri(String maCDDT, NgungDieuTriRequest r) {
        DotDieuTriResponse dot = dotRepo.findById(maCDDT)
                .orElseThrow(() -> BusinessException.notFound("đợt điều trị (tại chi nhánh này)", maCDDT));
        if (!ChanDoanDieuTri.DANG_DIEU_TRI.equals(dot.trangThai())) {
            throw new BusinessException(HttpStatus.CONFLICT, "DOT_DA_KET_THUC",
                    "Đợt \"" + dot.tenBenh() + "\" đã kết thúc (" + dot.trangThai() + ").");
        }
        LocalDateTime ngay = r != null && r.ngayKetThuc() != null ? r.ngayKetThuc().truncatedTo(ChronoUnit.SECONDS)
                : LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (ngay.isBefore(dot.luotGanNhat())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_DATE", "Ngày ngừng điều trị phải sau lượt gần nhất của đợt.");
        }
        int soLan = dot.soLanChuaCanThiet() != null ? dot.soLanChuaCanThiet() : 1;
        dotRepo.updateTienTrinh(maCDDT, soLan, ChanDoanDieuTri.NGUNG_DIEU_TRI, ngay);
        return dotRepo.findById(maCDDT).orElseThrow();
    }
}
