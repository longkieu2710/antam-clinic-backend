package vn.antam.clinic.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.ChanDoanRequest;
import vn.antam.clinic.dto.request.LuotKhamRequest;
import vn.antam.clinic.dto.response.BacSiResponse;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.dto.response.TaoLuotResponse;
import vn.antam.clinic.entity.Benh;
import vn.antam.clinic.entity.BenhNhan;
import vn.antam.clinic.entity.ChanDoanDieuTri;
import vn.antam.clinic.entity.LanKham;
import vn.antam.clinic.entity.LuotKhamChua;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BacSiRepository;
import vn.antam.clinic.repository.BenhNhanRepository;
import vn.antam.clinic.repository.BenhRepository;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.KhoaRepository;
import vn.antam.clinic.repository.LuotRepository;
import vn.antam.clinic.repository.ToanChuoiRepository;
import vn.antam.clinic.site.SiteContext;
import vn.antam.clinic.util.Codes;

/**
 * Tạo lượt khám tại chi nhánh đang chọn.
 * <p>
 * Hai bước đọc qua postgres_fdw chạy TRƯỚC transaction (lỗi FDW làm PostgreSQL huỷ cả transaction đang mở):
 * lấy hồ sơ bệnh nhân từ S0 nếu chi nhánh chưa có, và kiểm tra BR4 trên toàn chuỗi. Phần ghi chạy trong một
 * transaction tại chi nhánh: (bản sao BENH_NHAN) → LUOT_KHAM_CHUA → LAN_KHAM → CHAN_DOAN_DIEU_TRI → HO_TRO →
 * SU_DUNG_DV → KE_DON → SU_DUNG_TB.
 */
@Service
public class LuotKhamService {

    /** Số lần thử lại khi hai người cùng lưu và trùng mã lượt / mã đợt vừa sinh. */
    static final int SO_LAN_THU = 3;

    private final LuotRepository luotRepo;
    private final ChanDoanDieuTriRepository dotRepo;
    private final BenhNhanRepository benhNhanRepo;
    private final BacSiRepository bacSiRepo;
    private final KhoaRepository khoaRepo;
    private final BenhRepository benhRepo;
    private final ToanChuoiRepository toanChuoiRepo;
    private final ChiTietLuotService chiTietService;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public LuotKhamService(LuotRepository luotRepo, ChanDoanDieuTriRepository dotRepo, BenhNhanRepository benhNhanRepo,
                           BacSiRepository bacSiRepo, KhoaRepository khoaRepo, BenhRepository benhRepo,
                           ToanChuoiRepository toanChuoiRepo, ChiTietLuotService chiTietService, AppProperties props,
                           PlatformTransactionManager transactionManager) {
        this.luotRepo = luotRepo;
        this.dotRepo = dotRepo;
        this.benhNhanRepo = benhNhanRepo;
        this.bacSiRepo = bacSiRepo;
        this.khoaRepo = khoaRepo;
        this.benhRepo = benhRepo;
        this.toanChuoiRepo = toanChuoiRepo;
        this.chiTietService = chiTietService;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Không gắn @Transactional (kể cả NOT_SUPPORTED): khi có đồng bộ transaction, Spring giữ kết nối đầu tiên cho cả
     * phương thức nên {@link SiteContext#callOn} sang S0 sẽ vẫn chạy trên chi nhánh. Phần ghi tự mở transaction
     * bằng {@link TransactionTemplate}.
     */
    public TaoLuotResponse create(LuotKhamRequest r) {
        String site = SiteContext.get();
        LocalDateTime thoiGian = r.thoiGian() != null ? r.thoiGian().truncatedTo(ChronoUnit.SECONDS)
                : LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (thoiGian.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FUTURE_TIME", "Thời gian khám không được ở tương lai.");
        }
        if (r.ngayHenTaiKham() != null && !r.ngayHenTaiKham().isAfter(thoiGian.toLocalDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_APPOINTMENT", "Ngày hẹn tái khám phải sau ngày khám.");
        }
        List<ChanDoanRequest> chanDoan = r.chanDoan() == null ? List.of() : r.chanDoan();
        Set<String> benh = new HashSet<>();
        for (ChanDoanRequest c : chanDoan) {
            if (!benh.add(c.maBenh())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_ROW",
                        "Bệnh '" + c.maBenh() + "' được chẩn đoán 2 lần trong một lượt khám — vi phạm UNIQUE (MaLuotKham, MaBenh) (BR1).");
            }
        }

        // --- Đọc qua FDW, ngoài transaction ---
        BenhNhan banSao = benhNhanRepo.exists(r.maBN()) ? null : layHoSoToanChuoi(r.maBN());
        List<String> canhBao = new ArrayList<>();
        List<DotDieuTriResponse> dotMoNoiKhac = chanDoan.isEmpty() ? List.of() : dotDangMoChiNhanhKhac(r.maBN(), site, canhBao);
        for (DotDieuTriResponse d : dotMoNoiKhac) {
            if (benh.contains(d.maBenh())) {
                throw ChiTietLuotService.rule("BR4", "Bệnh nhân đang có đợt \"" + d.tenBenh() + "\" (" + d.maCDDT()
                        + ") chưa kết thúc tại chi nhánh " + d.maCn() + " — không mở đợt mới cùng bệnh (BR4).");
            }
        }

        // --- Ghi trong một transaction tại chi nhánh; trùng mã vừa sinh (lưu đồng thời) thì sinh lại ---
        for (int lan = 1; ; lan++) {
            try {
                return tx.execute(status -> ghi(r, thoiGian, chanDoan, banSao, site, canhBao));
            } catch (DuplicateKeyException e) {
                if (lan >= SO_LAN_THU) {
                    throw e;
                }
            }
        }
    }

    private TaoLuotResponse ghi(LuotKhamRequest r, LocalDateTime thoiGian, List<ChanDoanRequest> chanDoan,
                                BenhNhan banSao, String site, List<String> canhBao) {
        // BR6: khoa và bác sĩ phải của chi nhánh này (đọc bảng cục bộ); bác sĩ đang làm việc, đúng khoa
        khoaRepo.findById(r.maKhoa()).orElseThrow(() -> ChiTietLuotService.rule("BR6",
                "Khoa '" + r.maKhoa() + "' không thuộc chi nhánh này (BR6)."));
        BacSiResponse bs = bacSiRepo.findById(r.maBS()).orElseThrow(() -> ChiTietLuotService.rule("BR6",
                "Bác sĩ '" + r.maBS() + "' không thuộc chi nhánh này (BR6)."));
        if (NhanVien.NGUNG_HOAT_DONG.equals(bs.trangThai())) {
            throw ChiTietLuotService.rule("STAFF_INACTIVE", "Bác sĩ đã chọn (" + bs.hoTen() + ") đã ngừng hoạt động.");
        }
        if (!r.maKhoa().equals(bs.maKhoa())) {
            throw ChiTietLuotService.rule("WRONG_DEPARTMENT", "Bác sĩ đã chọn (" + bs.hoTen() + ") thuộc " + bs.tenKhoa()
                    + ", không khám ở khoa '" + r.maKhoa() + "'.");
        }

        // BR4 tại chi nhánh này (S0 có thể đang tắt nên luôn kiểm tra lại cục bộ, trong transaction)
        String ketLuan = Codes.blankToNull(r.ketLuanKham());
        if (!chanDoan.isEmpty()) {
            Map<String, Benh> catalog = benhRepo.search(null).stream()
                    .collect(Collectors.toMap(Benh::maBenh, Function.identity()));
            for (ChanDoanRequest c : chanDoan) {
                if (!catalog.containsKey(c.maBenh())) {
                    throw ChiTietLuotService.rule("NOT_IN_CATALOG", "Bệnh '" + c.maBenh() + "' chưa có trong danh mục tại chi nhánh.");
                }
            }
            if (ketLuan == null) {
                ketLuan = chanDoan.stream().map(c -> catalog.get(c.maBenh()).tenBenh()).collect(Collectors.joining("; "));
            }
            if (banSao == null) {
                for (DotDieuTriResponse d : dotRepo.findDangDieuTri(r.maBN())) {
                    if (chanDoan.stream().anyMatch(c -> c.maBenh().equals(d.maBenh()))) {
                        throw ChiTietLuotService.rule("BR4", "Bệnh nhân đang có đợt \"" + d.tenBenh() + "\" (" + d.maCDDT()
                                + ") chưa kết thúc — tạo lượt chữa cho đợt đó thay vì mở đợt mới (BR4).");
                    }
                }
            }
        }

        ChiTietLuotService.ChiTiet chiTiet = chiTietService.kiemTra(r, false);

        String prefix = Codes.luotPrefix("K", r.maKhoa(), r.maBS(), thoiGian);
        String maLuot = Codes.next(prefix, luotRepo.findMaLuotByPrefix(prefix), 3);

        boolean daChep = false;
        if (banSao != null && !benhNhanRepo.exists(banSao.maBN())) {
            benhNhanRepo.insert(banSao);
            daChep = true;
        }
        luotRepo.insert(new LuotKhamChua(maLuot, thoiGian, LuotKhamChua.LOAI_KHAM, r.ngayHenTaiKham(),
                Codes.blankToNull(r.ghiChu()), r.maKhoa(), r.maBS()));
        luotRepo.insert(new LanKham(maLuot, r.maBN(), Codes.blankToNull(r.lyDoKham()), Codes.blankToNull(r.trieuChung()),
                ketLuan != null ? ketLuan : "Không phát hiện bệnh"));

        List<String> maDot = new ArrayList<>();
        if (!chanDoan.isEmpty()) {
            String maCn = props.site(site).map(AppProperties.Site::maCn).orElse(site);
            List<String> existing = new ArrayList<>(dotRepo.findAllMaCDDT());
            for (ChanDoanRequest c : chanDoan) {
                String ma = Codes.next("CDDT-" + maCn + "-", existing, 3);
                existing.add(ma);
                dotRepo.insert(new ChanDoanDieuTri(ma, maLuot, c.maBenh(), Codes.blankToNull(c.mucDo()),
                        c.soLanChuaCanThiet(), null, ChanDoanDieuTri.DANG_DIEU_TRI));
                maDot.add(ma);
            }
        }

        ChiTietLuotService.ChiTiet rows = chiTiet.withMaLuot(maLuot);
        chiTietService.ghi(rows);

        String tenBN = banSao != null ? banSao.hoTen()
                : benhNhanRepo.findById(r.maBN()).map(b -> b.hoTen()).orElse(null);
        return new TaoLuotResponse(maLuot, LuotKhamChua.LOAI_KHAM, r.maBN(), tenBN, maDot, null, false, daChep,
                rows.tongTien(), List.copyOf(canhBao));
    }

    /** Bệnh nhân chưa có hồ sơ ở chi nhánh: lấy bản ghi từ view BENH_NHAN toàn cục ở S0 để chép sang (giữ mã). */
    private BenhNhan layHoSoToanChuoi(String maBN) {
        try {
            return SiteContext.callOn(props.hubSite(), () -> toanChuoiRepo.findBenhNhan(maBN))
                    .orElseThrow(() -> BusinessException.notFound("bệnh nhân (trên toàn chuỗi)", maBN));
        } catch (DataAccessException e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CHAIN_UNAVAILABLE",
                    "Bệnh nhân " + maBN + " chưa có hồ sơ ở chi nhánh này và không đọc được hồ sơ toàn chuỗi "
                            + "(trạm S0 hoặc một chi nhánh đang tắt). Thêm hồ sơ ở màn Bệnh nhân rồi tạo lượt lại.");
        }
    }

    /**
     * BR4 trên toàn chuỗi: các đợt "Đang điều trị" của bệnh nhân ở chi nhánh khác (view toàn cục ở S0).
     * S0 / chi nhánh khác tắt thì chỉ cảnh báo — chi nhánh vẫn làm việc được, BR4 cục bộ vẫn được kiểm tra.
     */
    private List<DotDieuTriResponse> dotDangMoChiNhanhKhac(String maBN, String site, List<String> canhBao) {
        String maCn = props.site(site).map(AppProperties.Site::maCn).orElse(site);
        try {
            return SiteContext.callOn(props.hubSite(), () -> dotRepo.findDangDieuTri(maBN)).stream()
                    .filter(d -> !maCn.equals(d.maCn()))
                    .toList();
        } catch (DataAccessException e) {
            canhBao.add("Không kiểm tra được đợt đang điều trị của bệnh nhân ở chi nhánh khác (trạm S0 hoặc một chi nhánh "
                    + "đang tắt) — BR4 mới được kiểm tra tại chi nhánh này.");
            return List.of();
        }
    }
}
