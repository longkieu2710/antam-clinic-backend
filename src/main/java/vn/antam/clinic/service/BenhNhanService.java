package vn.antam.clinic.service;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.request.BenhNhanRequest;
import vn.antam.clinic.dto.response.BenhNhanResponse;
import vn.antam.clinic.dto.response.BenhNhanToanChuoiResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.entity.BenhNhan;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BenhNhanRepository;
import vn.antam.clinic.util.Codes;

/**
 * Bệnh nhân của chi nhánh đang chọn. Mỗi chi nhánh giữ hồ sơ riêng (phân mảnh ngang), nhưng mã bệnh nhân là định danh
 * chung toàn chuỗi: bệnh nhân cũ đến chi nhánh mới thì tra cứu toàn chuỗi rồi dùng lại mã cũ.
 */
@Service
public class BenhNhanService {

    private final BenhNhanRepository repo;

    public BenhNhanService(BenhNhanRepository repo) {
        this.repo = repo;
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

    /**
     * Thêm bệnh nhân. Không bọc transaction: sinh mã phải đọc view toàn chuỗi qua postgres_fdw (lỗi FDW sẽ huỷ cả
     * transaction), còn phần ghi chỉ là một câu INSERT nên tự nó đã nguyên tử.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    /** Mã kế tiếp dạng BNnn, tính trên cả chuỗi để không trùng với bệnh nhân của chi nhánh khác. */
    private String nextMaBN() {
        try {
            return Codes.next("BN", repo.findAllMaBNToanChuoi(), 2);
        } catch (DataAccessException e) {
            throw chainUnavailable("Không đọc được danh sách bệnh nhân toàn chuỗi để sinh mã mới — nhập mã thủ công");
        }
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
