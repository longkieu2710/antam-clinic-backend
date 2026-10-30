package vn.antam.clinic.service;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.BenhNhanRequest;
import vn.antam.clinic.dto.response.BenhNhanResponse;
import vn.antam.clinic.dto.response.BenhNhanToanChuoiResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.entity.BenhNhan;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BenhNhanRepository;
import vn.antam.clinic.site.SiteContext;
import vn.antam.clinic.util.Codes;

/**
 * Bệnh nhân của chi nhánh đang chọn. Mỗi chi nhánh giữ hồ sơ riêng (phân mảnh ngang), nhưng mã bệnh nhân là định danh
 * chung toàn chuỗi: bệnh nhân cũ đến chi nhánh mới thì tra cứu toàn chuỗi rồi dùng lại mã cũ. Mã mới mang mã chi nhánh
 * cấp nó ({@code BN-CN1-0001}) nên chi nhánh vẫn thêm được bệnh nhân khi mất kết nối Trung tâm.
 */
@Service
public class BenhNhanService {

    private final BenhNhanRepository repo;
    private final AppProperties props;

    public BenhNhanService(BenhNhanRepository repo, AppProperties props) {
        this.repo = repo;
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

    /** Đọc qua postgres_fdw — không chạy trong transaction để lỗi FDW không làm hỏng transaction. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<BenhNhanToanChuoiResponse> searchToanChuoi(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        try {
            return repo.searchToanChuoi(q);
        } catch (DataAccessException e) {
            throw chainUnavailable("Không tra cứu được toàn chuỗi");
        }
    }

    /** Tra cứu tiếp đón chỉ trong chi nhánh — frontend dùng khi không tra cứu được toàn chuỗi (mất kết nối). */
    @Transactional(readOnly = true)
    public List<BenhNhanToanChuoiResponse> searchCucBo(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return repo.searchCucBo(q);
    }

    /**
     * Thêm bệnh nhân. Không gắn @Transactional nào, kể cả NOT_SUPPORTED: sinh mã đọc view toàn chuỗi qua postgres_fdw, lỗi
     * kết nối FDW mang SQLSTATE 08001 nên Hikari đóng luôn kết nối tới chi nhánh; nếu cả phương thức giữ một kết nối thì
     * câu đọc dự phòng ở bảng cục bộ sẽ lỗi "Connection is closed". Phần ghi chỉ là một câu INSERT nên tự nó đã nguyên tử.
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
     * mất kết nối. Có mạng thì tính trên cả chuỗi (tính cả mã đã chép sang chi nhánh khác rồi bị xoá ở đây), mất mạng thì
     * tính trên bảng cục bộ.
     */
    private String nextMaBN() {
        String site = SiteContext.get();
        String prefix = "BN-" + props.site(site).map(AppProperties.Site::maCn).orElse(site) + "-";
        List<String> existing;
        try {
            existing = repo.findAllMaBNToanChuoi();
        } catch (DataAccessException e) {
            existing = repo.findAllMaBN();
        }
        return Codes.next(prefix, existing, 4);
    }

    private static BusinessException chainUnavailable(String message) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CHAIN_UNAVAILABLE",
                message + " (trạm trung tâm S0 hoặc một chi nhánh đang tắt).");
    }

    private static BenhNhan toEntity(String maBN, BenhNhanRequest r) {
        return new BenhNhan(maBN, r.hoTen().trim(), r.ngaySinh(), Codes.blankToNull(r.gioiTinh()),
                Codes.blankToNull(r.cccd()), Codes.blankToNull(r.soDienThoai()), Codes.blankToNull(r.diaChi()),
                Codes.blankToNull(r.soTheBHYT()), Codes.blankToNull(r.nhomMau()), Codes.blankToNull(r.tienSuDiUng()));
    }
}
