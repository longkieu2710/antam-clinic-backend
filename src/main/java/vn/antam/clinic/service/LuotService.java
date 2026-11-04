package vn.antam.clinic.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.request.SuaLuotChuaRequest;
import vn.antam.clinic.dto.request.SuaLuotKhamRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.dto.response.LuotChiTietResponse;
import vn.antam.clinic.dto.response.LuotResponse;
import vn.antam.clinic.entity.ChanDoanDieuTri;
import vn.antam.clinic.entity.LanChua;
import vn.antam.clinic.entity.LanKham;
import vn.antam.clinic.entity.LuotKhamChua;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.LuotRepository;
import vn.antam.clinic.util.Codes;

/** Danh sách, chi tiết và xoá lượt khám / chữa nhập nhầm tại chi nhánh đang chọn. */
@Service
public class LuotService {

    /** Số dòng tối đa trả về cho danh sách lượt. */
    static final int GIOI_HAN = 500;

    private final LuotRepository luotRepo;
    private final ChanDoanDieuTriRepository dotRepo;

    public LuotService(LuotRepository luotRepo, ChanDoanDieuTriRepository dotRepo) {
        this.luotRepo = luotRepo;
        this.dotRepo = dotRepo;
    }

    /**
     * @param tu   ngày đầu (tính cả ngày này), null = không giới hạn
     * @param den  ngày cuối (tính cả ngày này), null = không giới hạn
     * @param loai KHAM | CHUA | null
     */
    @Transactional(readOnly = true)
    public List<LuotResponse> search(LocalDate tu, LocalDate den, String loai, String khoa, String bs, String q) {
        if (tu != null && den != null && den.isBefore(tu)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_RANGE", "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        String loaiLuot = Codes.blankToNull(loai);
        if (loaiLuot != null && !loaiLuot.equals(LuotKhamChua.LOAI_KHAM) && !loaiLuot.equals(LuotKhamChua.LOAI_CHUA)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_PARAM", "Loại lượt là KHAM hoặc CHUA.");
        }
        return luotRepo.search(tu != null ? tu.atStartOfDay() : null, den != null ? den.plusDays(1).atStartOfDay() : null,
                loaiLuot, Codes.blankToNull(khoa), Codes.blankToNull(bs), Codes.blankToNull(q), GIOI_HAN);
    }

    @Transactional(readOnly = true)
    public LuotChiTietResponse get(String maLuot) {
        LuotResponse luot = luotRepo.findById(maLuot).orElseThrow(() -> BusinessException.notFound("lượt", maLuot));
        List<DotDieuTriResponse> dot = LuotKhamChua.LOAI_KHAM.equals(luot.loaiLuot())
                ? dotRepo.findByLuotKham(maLuot)
                : luot.maCDDT() == null ? List.of() : dotRepo.findById(luot.maCDDT()).map(List::of).orElse(List.of());
        String lyDo = lyDoKhongXoa(luot);
        boolean kham = LuotKhamChua.LOAI_KHAM.equals(luot.loaiLuot());
        String lyDoSua = lyDoKhongSua(dot);
        return new LuotChiTietResponse(luot, luotRepo.findHinhThuc(maLuot), luotRepo.findYTa(maLuot), dot,
                luotRepo.findChiPhi(maLuot), lyDo == null, lyDo,
                kham ? luotRepo.findLuotChuaCuaLuotKham(maLuot) : List.of(), lyDoSua == null, lyDoSua);
    }

    /**
     * Sửa kết luận và ngày hẹn tái khám của một lượt khám. Khoá khi có đợt mở từ lượt này đã khỏi hoặc ngừng điều trị —
     * kết luận đã gắn với kết quả điều trị.
     */
    @Transactional
    public LuotChiTietResponse suaLuotKham(String maLuot, SuaLuotKhamRequest r) {
        LuotResponse luot = luotRepo.findById(maLuot).orElseThrow(() -> BusinessException.notFound("lượt", maLuot));
        if (!LuotKhamChua.LOAI_KHAM.equals(luot.loaiLuot())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NOT_EXAM", "Chỉ sửa được lượt khám.");
        }
        List<DotDieuTriResponse> dot = dotRepo.findByLuotKham(maLuot);
        String lyDo = lyDoKhongSua(dot);
        if (lyDo != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "DOT_DA_KET_THUC", lyDo);
        }
        if (r.ngayHenTaiKham() != null && !r.ngayHenTaiKham().isAfter(luot.thoiGian().toLocalDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_APPOINTMENT", "Ngày hẹn tái khám phải sau ngày khám.");
        }
        String ketLuan = Codes.blankToNull(r.ketLuanKham());
        if (ketLuan == null) {
            ketLuan = dot.isEmpty() ? LanKham.KHONG_PHAT_HIEN_BENH
                    : dot.stream().map(DotDieuTriResponse::tenBenh).collect(Collectors.joining("; "));
        }
        luotRepo.updateKetLuanKham(maLuot, ketLuan);
        luotRepo.updateNgayHenTaiKham(maLuot, r.ngayHenTaiKham());
        return get(maLuot);
    }

    /**
     * Sửa kết quả của một lượt chữa: tình trạng, kết luận, số lần chữa cần thiết của đợt, hẹn tái khám tiếp. Khoá khi đợt
     * đã khỏi / ngừng điều trị. "Khỏi" kết thúc đợt "Đã khỏi" tại thời điểm lượt chữa (BR10) nên chỉ ghi được ở lần chữa
     * cuối của đợt, và trong tháng hiện tại — thưởng chữa khỏi tính theo tháng đợt kết thúc, như quy tắc xoá lượt.
     */
    @Transactional
    public LuotChiTietResponse suaLuotChua(String maLuot, SuaLuotChuaRequest r) {
        LuotResponse luot = luotRepo.findById(maLuot).orElseThrow(() -> BusinessException.notFound("lượt", maLuot));
        if (!LuotKhamChua.LOAI_CHUA.equals(luot.loaiLuot())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NOT_TREATMENT", "Đây không phải lượt chữa.");
        }
        String tinhTrang = Codes.blankToNull(r.tinhTrangSauChua());
        if (tinhTrang != null && !LanChua.TINH_TRANG.contains(tinhTrang)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Tình trạng sau chữa là một trong: " + String.join(", ", LanChua.TINH_TRANG) + ".");
        }
        DotDieuTriResponse dot = dotRepo.findById(luot.maCDDT()).orElseThrow(() -> BusinessException.notFound("đợt", luot.maCDDT()));
        String lyDo = lyDoKhongSua(List.of(dot));
        if (lyDo != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "DOT_DA_KET_THUC", lyDo);
        }
        boolean khoi = LanChua.KHOI.equals(tinhTrang);
        if (khoi) {
            String cuoi = luotRepo.findLanChuaCuoi(dot.maCDDT()).orElse(maLuot);
            if (!cuoi.equals(maLuot)) {
                throw new BusinessException(HttpStatus.CONFLICT, "KHOI_KHONG_PHAI_LAN_CUOI", "Chỉ ghi \"Khỏi\" ở lần chữa "
                        + "cuối của đợt — đợt đã có lần chữa sau lần này (" + cuoi + ").");
            }
            if (luot.thoiGian().isBefore(YearMonth.now().atDay(1).atStartOfDay())) {
                throw new BusinessException(HttpStatus.CONFLICT, "HISTORY_LOCKED", "Chỉ ghi \"Khỏi\" cho lượt chữa trong "
                        + "tháng hiện tại — thưởng chữa khỏi của các tháng trước đã tính vào lương.");
            }
        } else if (r.ngayHenTaiKham() != null && !r.ngayHenTaiKham().isAfter(luot.thoiGian().toLocalDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_APPOINTMENT", "Ngày hẹn tái khám phải sau ngày chữa.");
        }
        luotRepo.updateKetQuaChua(maLuot, tinhTrang, Codes.blankToNull(r.ketLuanSauChua()));
        luotRepo.updateNgayHenTaiKham(maLuot, khoi ? null : r.ngayHenTaiKham());
        // BR8: bác sĩ được chỉnh số lần chữa dự kiến; BR10: "Khỏi" → đợt "Đã khỏi" (bác sĩ được thưởng ở F3)
        int soLan = r.soLanChuaCanThiet() != null ? r.soLanChuaCanThiet()
                : dot.soLanChuaCanThiet() != null ? dot.soLanChuaCanThiet() : 1;
        dotRepo.updateTienTrinh(dot.maCDDT(), soLan, khoi ? ChanDoanDieuTri.DA_KHOI : ChanDoanDieuTri.DANG_DIEU_TRI,
                khoi ? luot.thoiGian() : null);
        return get(maLuot);
    }

    /** null = sửa được lượt; ngược lại là lý do khoá (đợt của lượt đã khỏi / ngừng điều trị). */
    private static String lyDoKhongSua(List<DotDieuTriResponse> dot) {
        return dot.stream()
                .filter(d -> !ChanDoanDieuTri.DANG_DIEU_TRI.equals(d.trangThai()))
                .findFirst()
                .map(d -> "Đợt \"" + d.tenBenh() + "\" đã " + d.trangThai().toLowerCase()
                        + " — không sửa được lượt nữa.")
                .orElse(null);
    }

    /**
     * Xoá lượt nhập nhầm. Chỉ cho xoá khi không làm hỏng chuỗi khám/chữa:
     * <ul>
     *   <li>lượt trong tháng hiện tại (lương, doanh thu các tháng đã qua không bị tính lại);</li>
     *   <li>lượt chữa: phải là lần chữa cuối của đợt — nếu chính lần này kết thúc đợt ("Đã khỏi") thì đợt mở lại;</li>
     *   <li>lượt khám: các đợt mở từ lượt này chưa có lần chữa nào (khoá ngoại CASCADE xoá kèm các đợt).</li>
     * </ul>
     */
    @Transactional
    public DeleteResponse delete(String maLuot) {
        LuotResponse luot = luotRepo.findById(maLuot).orElseThrow(() -> BusinessException.notFound("lượt", maLuot));
        String lyDo = lyDoKhongXoa(luot);
        if (lyDo != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_DELETE", lyDo);
        }
        String message;
        if (LuotKhamChua.LOAI_CHUA.equals(luot.loaiLuot())) {
            DotDieuTriResponse dot = dotRepo.findById(luot.maCDDT()).orElseThrow();
            boolean moLai = ChanDoanDieuTri.DA_KHOI.equals(dot.trangThai())
                    && Objects.equals(dot.ngayKetThuc(), luot.thoiGian());
            luotRepo.delete(maLuot);
            if (moLai) {
                dotRepo.updateTienTrinh(dot.maCDDT(), dot.soLanChuaCanThiet() != null ? dot.soLanChuaCanThiet() : 1,
                        ChanDoanDieuTri.DANG_DIEU_TRI, null);
            }
            message = "Đã xoá lần chữa " + luot.lanThu() + " (" + maLuot + ")"
                    + (moLai ? " — đợt \"" + dot.tenBenh() + "\" được mở lại \"Đang điều trị\"." : ".");
        } else {
            int soDot = dotRepo.findByLuotKham(maLuot).size();
            luotRepo.delete(maLuot);
            message = "Đã xoá lượt khám " + maLuot + (soDot > 0 ? " và " + soDot + " đợt điều trị mở từ lượt này." : ".");
        }
        return DeleteResponse.hard(message);
    }

    /** null = xoá được; ngược lại là lý do không xoá. */
    private String lyDoKhongXoa(LuotResponse luot) {
        LocalDateTime dauThang = YearMonth.now().atDay(1).atStartOfDay();
        if (luot.thoiGian().isBefore(dauThang)) {
            return "Chỉ xoá được lượt trong tháng hiện tại — lương và doanh thu các tháng trước đã dùng lượt này.";
        }
        if (LuotKhamChua.LOAI_CHUA.equals(luot.loaiLuot())) {
            String cuoi = luotRepo.findLanChuaCuoi(luot.maCDDT()).orElse(luot.maLuot());
            if (!cuoi.equals(luot.maLuot())) {
                return "Đợt đã có lần chữa sau lần này (" + cuoi + ") — chỉ xoá được lần chữa cuối của đợt.";
            }
            return null;
        }
        int soLanChua = luotRepo.countLanChuaCuaLuotKham(luot.maLuot());
        if (soLanChua > 0) {
            return "Đợt điều trị mở từ lượt khám này đã có " + soLanChua + " lần chữa — xoá các lần chữa trước.";
        }
        return null;
    }
}
