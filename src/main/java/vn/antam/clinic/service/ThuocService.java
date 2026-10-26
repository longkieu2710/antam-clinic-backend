package vn.antam.clinic.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.request.ThuocRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.ThuocResponse;
import vn.antam.clinic.entity.Thuoc;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.ThuocRepository;
import vn.antam.clinic.util.Codes;

/**
 * Thuốc — danh mục chung. Ghi tại S0 ({@code /api/master/thuoc}), Logical Replication tự đẩy xuống S1..S3;
 * chi nhánh chỉ đọc bản sao ({@code /api/thuoc}).
 */
@Service
public class ThuocService {

    private final ThuocRepository repo;

    public ThuocService(ThuocRepository repo) {
        this.repo = repo;
    }

    /**
     * Danh sách ở S0 kèm số lần kê trên toàn chuỗi. Không chạy trong transaction: nếu đọc KE_DON lỗi
     * (một chi nhánh tắt) thì transaction sẽ bị PostgreSQL huỷ — ở đây vẫn trả danh sách, chỉ thiếu số lần kê.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ThuocResponse> listMaster(String q) {
        List<Thuoc> rows = repo.search(q);
        Map<String, Long> usage = usageAcrossChain();
        return rows.stream()
                .map(t -> ThuocResponse.from(t, usage == null ? null : usage.getOrDefault(t.maThuoc(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ThuocResponse> listReplica(String q) {
        return repo.search(q).stream().map(t -> ThuocResponse.from(t, null)).toList();
    }

    @Transactional
    public ThuocResponse create(ThuocRequest r) {
        String ma = Codes.blankToNull(r.maThuoc());
        if (ma == null) {
            ma = Codes.next("T", repo.findAllMaThuoc(), 2);
        }
        Thuoc thuoc = toEntity(ma, r);
        repo.insert(thuoc);
        return ThuocResponse.from(thuoc, 0L);
    }

    /** Đổi đơn giá không ảnh hưởng lượt cũ: KE_DON lưu DonGiaApDung tại thời điểm kê (BR7). */
    @Transactional
    public ThuocResponse update(String maThuoc, ThuocRequest r) {
        Thuoc thuoc = toEntity(maThuoc, r);
        if (repo.update(thuoc) == 0) {
            throw BusinessException.notFound("thuốc", maThuoc);
        }
        return ThuocResponse.from(thuoc, null);
    }

    /**
     * Chi nhánh KHÔNG kiểm tra khoá ngoại khi nhận dữ liệu đồng bộ, nên trước khi xoá ở S0
     * phải tự kiểm tra thuốc đã được kê ở chi nhánh nào chưa (qua view KE_DON toàn cục).
     */
    // Không bọc transaction: câu đếm qua postgres_fdw lỗi (một chi nhánh tắt) làm PostgreSQL huỷ transaction và Hikari
    // đóng kết nối → rollback lỗi đè mất thông báo 503. Câu DELETE ở S0 chỉ một lệnh nên tự nó đã nguyên tử.
    public DeleteResponse delete(String maThuoc) {
        Map<String, Long> usage = usageAcrossChain();
        if (usage == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CHECK_UNAVAILABLE",
                    "Không đọc được đơn thuốc ở các chi nhánh (một trạm đang tắt) nên tạm không cho xoá.");
        }
        long used = usage.getOrDefault(maThuoc, 0L);
        if (used > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "IN_USE",
                    "Thuốc " + maThuoc + " đã được kê trong " + used + " đơn ở toàn chuỗi — không thể xoá.");
        }
        if (repo.delete(maThuoc) == 0) {
            throw BusinessException.notFound("thuốc", maThuoc);
        }
        return DeleteResponse.hard("Đã xoá thuốc " + maThuoc + " tại S0 — Logical Replication sẽ xoá bản sao ở các chi nhánh.");
    }

    /** @return số lần kê theo mã thuốc, hoặc null nếu không đọc được view toàn cục */
    private Map<String, Long> usageAcrossChain() {
        try {
            return repo.countKeDonByThuoc();
        } catch (DataAccessException e) {
            return null;
        }
    }

    private static Thuoc toEntity(String maThuoc, ThuocRequest r) {
        return new Thuoc(maThuoc, r.tenThuoc().trim(), Codes.blankToNull(r.hoatChat()), r.donViTinh().trim(),
                r.donGia(), Codes.blankToNull(r.hangSX()));
    }
}
