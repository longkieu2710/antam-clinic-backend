package vn.antam.clinic.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.antam.clinic.dto.request.ChiTietLuotRequest;
import vn.antam.clinic.dto.request.DichVuDungRequest;
import vn.antam.clinic.dto.request.ThietBiDungRequest;
import vn.antam.clinic.dto.request.ThuocKeRequest;
import vn.antam.clinic.dto.request.YTaHoTroRequest;
import vn.antam.clinic.dto.response.ThietBiResponse;
import vn.antam.clinic.dto.response.YTaResponse;
import vn.antam.clinic.entity.DichVu;
import vn.antam.clinic.entity.HoTro;
import vn.antam.clinic.entity.KeDon;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.entity.SuDungDv;
import vn.antam.clinic.entity.SuDungTb;
import vn.antam.clinic.entity.ThietBi;
import vn.antam.clinic.entity.Thuoc;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.DichVuRepository;
import vn.antam.clinic.repository.LuotRepository;
import vn.antam.clinic.repository.ThietBiRepository;
import vn.antam.clinic.repository.ThuocRepository;
import vn.antam.clinic.repository.YTaRepository;
import vn.antam.clinic.util.Codes;

/**
 * Phần chung của lượt khám và lượt chữa: y tá hỗ trợ, dịch vụ, thuốc, thiết bị.
 * Không tự mở transaction — luôn được gọi bên trong transaction ghi lượt của {@link LuotKhamService} /
 * {@link LuotChuaService} nên đọc danh mục và ghi cùng một kết nối tới chi nhánh.
 */
@Service
public class ChiTietLuotService {

    private final YTaRepository yTaRepo;
    private final DichVuRepository dichVuRepo;
    private final ThuocRepository thuocRepo;
    private final ThietBiRepository thietBiRepo;
    private final LuotRepository luotRepo;

    public ChiTietLuotService(YTaRepository yTaRepo, DichVuRepository dichVuRepo, ThuocRepository thuocRepo,
                              ThietBiRepository thietBiRepo, LuotRepository luotRepo) {
        this.yTaRepo = yTaRepo;
        this.dichVuRepo = dichVuRepo;
        this.thuocRepo = thuocRepo;
        this.thietBiRepo = thietBiRepo;
        this.luotRepo = luotRepo;
    }

    /** Các dòng đã kiểm tra, đơn giá áp dụng đã điền từ danh mục; mã lượt điền sau bằng {@link #withMaLuot}. */
    public record ChiTiet(List<HoTro> yTa, List<SuDungDv> dichVu, List<KeDon> thuoc, List<SuDungTb> thietBi) {

        public BigDecimal tongTien() {
            BigDecimal tong = BigDecimal.ZERO;
            for (SuDungDv d : dichVu) {
                tong = tong.add(d.donGiaApDung().multiply(BigDecimal.valueOf(d.soLuong())));
            }
            for (KeDon k : thuoc) {
                tong = tong.add(k.donGiaApDung().multiply(BigDecimal.valueOf(k.soLuong())));
            }
            for (SuDungTb s : thietBi) {
                tong = tong.add(s.donGiaApDung().multiply(BigDecimal.valueOf(s.soLan())));
            }
            return tong;
        }

        public ChiTiet withMaLuot(String maLuot) {
            return new ChiTiet(
                    yTa.stream().map(y -> new HoTro(y.maYT(), maLuot, y.vaiTro())).toList(),
                    dichVu.stream().map(d -> new SuDungDv(maLuot, d.maDV(), d.soLuong(), d.donGiaApDung(), d.ketQua())).toList(),
                    thuoc.stream().map(k -> new KeDon(maLuot, k.maThuoc(), k.soLuong(), k.lieuDung(), k.donGiaApDung())).toList(),
                    thietBi.stream().map(s -> new SuDungTb(maLuot, s.maTB(), s.soLan(), s.donGiaApDung())).toList());
        }
    }

    /**
     * Kiểm tra các dòng và lấy đơn giá tại thời điểm lưu (BR7).
     * <ul>
     *   <li>BR6: y tá, thiết bị phải thuộc chi nhánh (đọc bảng cục bộ), y tá đang làm việc, thiết bị chưa ngừng hoạt động.</li>
     *   <li>Mỗi mã chỉ một dòng — trùng sẽ vi phạm khoá chính (MaLuot, Ma…), báo trước để người dùng gộp số lượng.</li>
     *   <li>BR11: lượt chữa không tính dịch vụ loại "Khám bệnh" (tái khám trong đợt miễn phí khám).</li>
     * </ul>
     */
    public ChiTiet kiemTra(ChiTietLuotRequest r, boolean luotChua) {
        List<HoTro> yTa = new ArrayList<>();
        for (YTaHoTroRequest y : unique(r.yTa(), YTaHoTroRequest::maYT, "y tá")) {
            YTaResponse nv = yTaRepo.findById(y.maYT()).orElseThrow(() -> rule("BR6",
                    "Y tá '" + y.maYT() + "' không thuộc chi nhánh này (BR6)."));
            if (NhanVien.NGUNG_HOAT_DONG.equals(nv.trangThai())) {
                throw rule("STAFF_INACTIVE", "Y tá đã chọn (" + nv.hoTen() + ") đã ngừng hoạt động — không chọn hỗ trợ được.");
            }
            yTa.add(new HoTro(y.maYT(), null, Codes.blankToNull(y.vaiTro())));
        }

        List<SuDungDv> dichVu = new ArrayList<>();
        List<DichVuDungRequest> dvRows = unique(r.dichVu(), DichVuDungRequest::maDV, "dịch vụ");
        if (!dvRows.isEmpty()) {
            Map<String, DichVu> catalog = dichVuRepo.search(null).stream()
                    .collect(Collectors.toMap(DichVu::maDV, Function.identity()));
            for (DichVuDungRequest d : dvRows) {
                DichVu dv = catalog.get(d.maDV());
                if (dv == null) {
                    throw rule("NOT_IN_CATALOG", "Dịch vụ '" + d.maDV() + "' chưa có trong danh mục tại chi nhánh "
                            + "(danh mục ghi ở S0, đồng bộ xuống sau vài giây).");
                }
                if (luotChua && DichVu.LOAI_KHAM_BENH.equals(dv.loaiDV())) {
                    throw rule("BR11", "Lần chữa trong đợt điều trị không tính tiền khám — bỏ dịch vụ \""
                            + dv.tenDV() + "\" (BR11).");
                }
                dichVu.add(new SuDungDv(null, dv.maDV(), d.soLuong(), dv.donGia(), Codes.blankToNull(d.ketQua())));
            }
        }

        List<KeDon> thuoc = new ArrayList<>();
        List<ThuocKeRequest> thuocRows = unique(r.thuoc(), ThuocKeRequest::maThuoc, "thuốc");
        if (!thuocRows.isEmpty()) {
            Map<String, Thuoc> catalog = thuocRepo.search(null).stream()
                    .collect(Collectors.toMap(Thuoc::maThuoc, Function.identity()));
            for (ThuocKeRequest k : thuocRows) {
                Thuoc t = catalog.get(k.maThuoc());
                if (t == null) {
                    throw rule("NOT_IN_CATALOG", "Thuốc '" + k.maThuoc() + "' chưa có trong danh mục tại chi nhánh.");
                }
                thuoc.add(new KeDon(null, t.maThuoc(), k.soLuong(), Codes.blankToNull(k.lieuDung()), t.donGia()));
            }
        }

        List<SuDungTb> thietBi = new ArrayList<>();
        for (ThietBiDungRequest s : unique(r.thietBi(), ThietBiDungRequest::maTB, "thiết bị")) {
            ThietBiResponse tb = thietBiRepo.findById(s.maTB()).orElseThrow(() -> rule("BR6",
                    "Thiết bị '" + s.maTB() + "' không thuộc chi nhánh này (BR6)."));
            if (ThietBi.NGUNG_HOAT_DONG.equals(tb.tinhTrang())) {
                throw rule("DEVICE_INACTIVE", "Thiết bị \"" + tb.tenTB() + "\" đã ngừng hoạt động.");
            }
            thietBi.add(new SuDungTb(null, tb.maTB(), s.soLan(), tb.donGiaSuDung()));
        }
        return new ChiTiet(yTa, dichVu, thuoc, thietBi);
    }

    /** Ghi các dòng của một lượt (phải gọi sau khi đã INSERT LUOT_KHAM_CHUA). */
    public void ghi(ChiTiet c) {
        c.yTa().forEach(luotRepo::insert);
        c.dichVu().forEach(luotRepo::insert);
        c.thuoc().forEach(luotRepo::insert);
        c.thietBi().forEach(luotRepo::insert);
    }

    private static <T> List<T> unique(List<T> rows, Function<T, String> key, String what) {
        if (rows == null) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        for (T row : rows) {
            if (!seen.add(key.apply(row))) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_ROW",
                        "Mỗi " + what + " chỉ nhập một dòng — '" + key.apply(row) + "' bị nhập 2 lần, hãy gộp số lượng.");
            }
        }
        return rows;
    }

    static BusinessException rule(String code, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
