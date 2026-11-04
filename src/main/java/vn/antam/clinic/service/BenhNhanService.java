package vn.antam.clinic.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.BenhNhanRequest;
import vn.antam.clinic.dto.response.BenhNhanResponse;
import vn.antam.clinic.dto.response.BenhNhanTiepDonResponse;
import vn.antam.clinic.dto.response.ChiMucBenhNhanResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.MaToanChuoiResponse;
import vn.antam.clinic.dto.response.TiepDonResponse;
import vn.antam.clinic.entity.BenhNhan;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BenhNhanRepository;
import vn.antam.clinic.repository.ToanChuoiRepository;
import vn.antam.clinic.site.SiteContext;
import vn.antam.clinic.util.Codes;

/**
 * Bệnh nhân của chi nhánh đang chọn. Mỗi chi nhánh giữ hồ sơ riêng (phân mảnh ngang) và không đọc được hồ sơ của
 * chi nhánh khác; mã bệnh nhân là định danh chung toàn chuỗi: bệnh nhân cũ đến chi nhánh mới thì tiếp đón nhập đúng số
 * CCCD / thẻ BHYT, hàm tim_ma_benh_nhan ở S0 chỉ trả mã cũ, chi nhánh lập hồ sơ của mình với mã đó từ giấy tờ bệnh nhân
 * đưa. Mã mới mang mã chi nhánh cấp nó ({@code BN-CN1-0001}) nên vẫn thêm được bệnh nhân khi mất kết nối Trung tâm.
 */
@Service
public class BenhNhanService {

    private final BenhNhanRepository repo;
    private final ToanChuoiRepository toanChuoiRepo;
    private final AppProperties props;

    public BenhNhanService(BenhNhanRepository repo, ToanChuoiRepository toanChuoiRepo, AppProperties props) {
        this.repo = repo;
        this.toanChuoiRepo = toanChuoiRepo;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<BenhNhanResponse> search(String q) {
        return repo.search(q);
    }

    @Transactional(readOnly = true)
    public BenhNhanResponse get(String maBN) {
        return repo.findById(maBN).orElseThrow(() -> BusinessException.notFound("bệnh nhân", maBN));
    }

    /**
     * Tiếp đón: tìm hồ sơ tại chi nhánh; nếu từ khoá là số CCCD / thẻ BHYT đầy đủ mà chi nhánh chưa có thì hỏi S0 xem số đó đã
     * có mã ở chi nhánh nào (chỉ mã, không thông tin cá nhân). Không gắn @Transactional: phần gọi S0 dùng
     * {@link SiteContext#callOn}, mất kết nối thì vẫn trả kết quả tại chi nhánh kèm cảnh báo.
     */
    public TiepDonResponse tiepDon(String q) {
        if (q == null || q.isBlank()) {
            return new TiepDonResponse(List.of(), List.of(), false, null);
        }
        String so = q.trim();
        List<BenhNhanTiepDonResponse> taiChiNhanh = repo.searchTiepDon(so);
        boolean laSoDinhDanh = so.length() >= 9 && so.chars().noneMatch(Character::isWhitespace);
        boolean daCoTaiDay = taiChiNhanh.stream()
                .anyMatch(b -> so.equalsIgnoreCase(b.cccd()) || so.equalsIgnoreCase(b.soTheBHYT()));
        if (!laSoDinhDanh || daCoTaiDay) {
            return new TiepDonResponse(taiChiNhanh, List.of(), false, null);
        }
        String maCn = maCnDangChon();
        List<ChiMucBenhNhanResponse> rows;
        try {
            rows = SiteContext.callOn(props.hubSite(), () -> toanChuoiRepo.timMaBenhNhan(so));
        } catch (DataAccessException e) {
            return new TiepDonResponse(taiChiNhanh, List.of(), false,
                    "Không kết nối được Trung tâm — chỉ tra được hồ sơ tại chi nhánh này.");
        }
        List<ChiMucBenhNhanResponse> noiKhac = rows.stream().filter(r -> r.ketNoi() && !maCn.equals(r.maCn())).toList();
        List<String> matKetNoi = rows.stream().filter(r -> !r.ketNoi()).map(ChiMucBenhNhanResponse::maCn).toList();
        String canhBao = matKetNoi.isEmpty() ? null
                : "Chi nhánh " + String.join(", ", matKetNoi) + " đang mất kết nối — hồ sơ ở đó (nếu có) chưa tra được.";
        return new TiepDonResponse(taiChiNhanh, noiKhac, true, canhBao);
    }

    /**
     * Thêm bệnh nhân. Không gắn @Transactional: sinh mã đọc S0 bằng {@link SiteContext#callOn} (chỉ có tác dụng ngoài
     * transaction). Phần ghi chỉ là một câu INSERT nên tự nó đã nguyên tử.
     */
    public BenhNhanResponse create(BenhNhanRequest r) {
        String maBN = Codes.blankToNull(r.maBN());
        if (maBN == null) {
            maBN = nextMaBN();
        }
        repo.insert(toEntity(maBN, r));
        return get(maBN);
    }

    @Transactional
    public BenhNhanResponse update(String maBN, BenhNhanRequest r) {
        if (repo.update(toEntity(maBN, r)) == 0) {
            throw BusinessException.notFound("bệnh nhân", maBN);
        }
        return get(maBN);
    }

    /**
     * BR9: bệnh nhân đã có lần khám thì không xoá. Bảng BENH_NHAN không có cột trạng thái nên không thể chuyển
     * "Ngừng hoạt động" như nhân viên — từ chối xoá.
     */
    @Transactional
    public DeleteResponse delete(String maBN) {
        BenhNhanResponse bn = get(maBN);
        if (bn.soLanKham() > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "IN_USE",
                    bn.hoTen() + " đã có " + bn.soLanKham() + " lần khám tại chi nhánh — không xoá được hồ sơ (BR9).");
        }
        if (repo.delete(maBN) == 0) {
            throw BusinessException.notFound("bệnh nhân", maBN);
        }
        return DeleteResponse.hard("Đã xoá hồ sơ bệnh nhân " + bn.hoTen() + " (" + maBN + ") tại chi nhánh.");
    }

    /**
     * Mã kế tiếp dạng BN-{MaCN}-nnnn. Chỉ chi nhánh này cấp mã mang tiền tố của nó, nên không trùng chi nhánh khác kể cả khi
     * mất kết nối. Có mạng thì tính thêm các mã cùng tiền tố đang ở chi nhánh khác (bệnh nhân đã lập hồ sơ ở đó rồi hồ sơ ở
     * đây bị xoá); mất mạng thì chỉ tính trên bảng cục bộ.
     */
    private String nextMaBN() {
        String prefix = "BN-" + maCnDangChon() + "-";
        List<String> existing = new ArrayList<>(repo.findAllMaBN());
        try {
            SiteContext.callOn(props.hubSite(), () -> toanChuoiRepo.maBenhNhanTheoTienTo(prefix)).stream()
                    .filter(MaToanChuoiResponse::ketNoi)
                    .map(MaToanChuoiResponse::ma)
                    .forEach(existing::add);
        } catch (DataAccessException e) {
            // mất kết nối Trung tâm: mã của chi nhánh này vẫn chỉ do chi nhánh này cấp
        }
        return Codes.next(prefix, existing, 4);
    }

    private String maCnDangChon() {
        String site = SiteContext.get();
        return props.site(site).map(AppProperties.Site::maCn).orElse(site);
    }

    private static BenhNhan toEntity(String maBN, BenhNhanRequest r) {
        return new BenhNhan(maBN, r.hoTen().trim(), r.ngaySinh(), Codes.blankToNull(r.gioiTinh()),
                Codes.blankToNull(r.cccd()), Codes.blankToNull(r.soDienThoai()), Codes.blankToNull(r.diaChi()),
                Codes.blankToNull(r.soTheBHYT()), Codes.blankToNull(r.nhomMau()), Codes.blankToNull(r.tienSuDiUng()));
    }
}
