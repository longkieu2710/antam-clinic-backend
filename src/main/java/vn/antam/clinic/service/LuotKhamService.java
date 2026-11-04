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
import vn.antam.clinic.dto.response.MaToanChuoiResponse;
import vn.antam.clinic.dto.response.TaoLuotResponse;
import vn.antam.clinic.entity.Benh;
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
 * Bệnh nhân phải có hồ sơ tại chi nhánh (lập ở màn Tiếp đón / Bệnh nhân) — chi nhánh không chép hồ sơ của chi nhánh
 * khác. BR4 trên toàn chuỗi hỏi hàm benh_dang_dieu_tri_noi_khac ở S0 (chỉ trả mã bệnh) TRƯỚC transaction. Phần ghi chạy
 * trong một transaction tại chi nhánh: LUOT_KHAM_CHUA → LAN_KHAM → CHAN_DOAN_DIEU_TRI → HO_TRO → SU_DUNG_DV → KE_DON →
 * SU_DUNG_TB.
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

        if (!benhNhanRepo.exists(r.maBN())) {
            throw ChiTietLuotService.rule("NO_LOCAL_RECORD", "Bệnh nhân " + r.maBN() + " chưa có hồ sơ tại chi nhánh này. "
                    + "Lập hồ sơ ở màn Tiếp đón (nhập số CCCD / thẻ BHYT để dùng lại mã cũ) rồi tạo lượt.");
        }

        // --- BR4 trên toàn chuỗi: hỏi S0, ngoài transaction ---
        List<String> canhBao = new ArrayList<>();
        List<String> dangDieuTriNoiKhac = benhDangDieuTriNoiKhac(r.maBN(), benh, site, canhBao);
        if (!dangDieuTriNoiKhac.isEmpty()) {
            String maBenh = dangDieuTriNoiKhac.getFirst();
            String tenBenh = benhRepo.search(null).stream().filter(b -> b.maBenh().equals(maBenh))
                    .map(Benh::tenBenh).findFirst().orElse(maBenh);
            throw ChiTietLuotService.rule("BR4", "Bệnh nhân đang có đợt điều trị \"" + tenBenh
                    + "\" chưa kết thúc ở chi nhánh khác — không mở đợt mới cùng bệnh (BR4).");
        }

        // --- Ghi trong một transaction tại chi nhánh; trùng mã vừa sinh (lưu đồng thời) thì sinh lại ---
        for (int lan = 1; ; lan++) {
            try {
                return tx.execute(status -> ghi(r, thoiGian, chanDoan, site, canhBao));
            } catch (DuplicateKeyException e) {
                if (lan >= SO_LAN_THU) {
                    throw e;
                }
            }
        }
    }

    private TaoLuotResponse ghi(LuotKhamRequest r, LocalDateTime thoiGian, List<ChanDoanRequest> chanDoan,
                                String site, List<String> canhBao) {
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
            for (DotDieuTriResponse d : dotRepo.findDangDieuTri(r.maBN())) {
                if (chanDoan.stream().anyMatch(c -> c.maBenh().equals(d.maBenh()))) {
                    throw ChiTietLuotService.rule("BR4", "Bệnh nhân đang có đợt \"" + d.tenBenh() + "\" (" + d.maCDDT()
                            + ") chưa kết thúc — tạo lượt chữa cho đợt đó thay vì mở đợt mới (BR4).");
                }
            }
        }

        ChiTietLuotService.ChiTiet chiTiet = chiTietService.kiemTra(r, false);

        String prefix = Codes.luotPrefix("K", r.maKhoa(), r.maBS(), thoiGian);
        String maLuot = Codes.next(prefix, luotRepo.findMaLuotByPrefix(prefix), 3);

        luotRepo.insert(new LuotKhamChua(maLuot, thoiGian, LuotKhamChua.LOAI_KHAM, r.ngayHenTaiKham(),
                Codes.blankToNull(r.ghiChu()), r.maKhoa(), r.maBS()));
        luotRepo.insert(new LanKham(maLuot, r.maBN(), Codes.blankToNull(r.lyDoKham()), Codes.blankToNull(r.trieuChung()),
                ketLuan != null ? ketLuan : LanKham.KHONG_PHAT_HIEN_BENH));

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

        String tenBN = benhNhanRepo.findById(r.maBN()).map(b -> b.hoTen()).orElse(null);
        return new TaoLuotResponse(maLuot, LuotKhamChua.LOAI_KHAM, r.maBN(), tenBN, maDot, null,
                rows.tongTien(), List.copyOf(canhBao));
    }

    /**
     * BR4 trên toàn chuỗi: trong các bệnh vừa chẩn đoán, bệnh nào bệnh nhân đang điều trị dở ở chi nhánh khác (hàm
     * benh_dang_dieu_tri_noi_khac ở S0 — chỉ trả mã bệnh, không lộ chi nhánh hay hồ sơ). S0 hoặc một chi nhánh tắt thì
     * chỉ cảnh báo — chi nhánh vẫn làm việc được, BR4 cục bộ vẫn được kiểm tra.
     */
    private List<String> benhDangDieuTriNoiKhac(String maBN, Set<String> benh, String site, List<String> canhBao) {
        if (benh.isEmpty()) {
            return List.of();
        }
        String maCn = props.site(site).map(AppProperties.Site::maCn).orElse(site);
        List<MaToanChuoiResponse> rows;
        try {
            rows = SiteContext.callOn(props.hubSite(),
                    () -> toanChuoiRepo.benhDangDieuTriNoiKhac(maBN, List.copyOf(benh), maCn));
        } catch (DataAccessException e) {
            canhBao.add("Không kiểm tra được đợt đang điều trị của bệnh nhân ở chi nhánh khác (mất kết nối Trung tâm) — "
                    + "BR4 mới được kiểm tra tại chi nhánh này.");
            return List.of();
        }
        if (rows.stream().anyMatch(x -> !x.ketNoi())) {
            canhBao.add("Một chi nhánh đang mất kết nối — BR4 chưa kiểm tra được ở chi nhánh đó.");
        }
        return rows.stream().filter(MaToanChuoiResponse::ketNoi).map(MaToanChuoiResponse::ma).toList();
    }
}
