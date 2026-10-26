package vn.antam.clinic.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.request.DichVuRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.DichVuResponse;
import vn.antam.clinic.entity.DichVu;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.DichVuRepository;
import vn.antam.clinic.util.Codes;

/**
 * Dịch vụ y tế — danh mục chung. Ghi tại S0 ({@code /api/master/dich-vu}), Logical Replication tự đẩy xuống S1..S3;
 * chi nhánh chỉ đọc bản sao ({@code /api/dich-vu}).
 */
@Service
public class DichVuService {

    private final DichVuRepository repo;

    public DichVuService(DichVuRepository repo) {
        this.repo = repo;
    }

    /** Danh sách ở S0 kèm số lần sử dụng toàn chuỗi; không chạy trong transaction (xem ThuocService.listMaster). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<DichVuResponse> listMaster(String q) {
        List<DichVu> rows = repo.search(q);
        Map<String, Long> usage = usageAcrossChain();
        return rows.stream()
                .map(d -> DichVuResponse.from(d, usage == null ? null : usage.getOrDefault(d.maDV(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DichVuResponse> listReplica(String q) {
        return repo.search(q).stream().map(d -> DichVuResponse.from(d, null)).toList();
    }

    @Transactional
    public DichVuResponse create(DichVuRequest r) {
        String ma = Codes.blankToNull(r.maDV());
        if (ma == null) {
            ma = Codes.next("DV", repo.findAllMaDV(), 2);
        }
        DichVu dv = toEntity(ma, r);
        repo.insert(dv);
        return DichVuResponse.from(dv, 0L);
    }

    /** Đổi đơn giá không ảnh hưởng lượt cũ: SU_DUNG_DV lưu DonGiaApDung tại thời điểm dùng (BR7). */
    @Transactional
    public DichVuResponse update(String maDV, DichVuRequest r) {
        DichVu dv = toEntity(maDV, r);
        if (repo.update(dv) == 0) {
            throw BusinessException.notFound("dịch vụ", maDV);
        }
        return DichVuResponse.from(dv, null);
    }

    /** Kiểm tra dịch vụ đã được dùng ở chi nhánh nào chưa (view SU_DUNG_DV toàn cục) rồi mới xoá ở S0. */
    // Không bọc transaction: câu đếm qua postgres_fdw lỗi (một chi nhánh tắt) làm PostgreSQL huỷ transaction và Hikari
    // đóng kết nối → rollback lỗi đè mất thông báo 503. Câu DELETE ở S0 chỉ một lệnh nên tự nó đã nguyên tử.
    public DeleteResponse delete(String maDV) {
        Map<String, Long> usage = usageAcrossChain();
        if (usage == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CHECK_UNAVAILABLE",
                    "Không đọc được dữ liệu sử dụng dịch vụ ở các chi nhánh (một trạm đang tắt) nên tạm không cho xoá.");
        }
        long used = usage.getOrDefault(maDV, 0L);
        if (used > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "IN_USE",
                    "Dịch vụ " + maDV + " đã được dùng trong " + used + " lượt ở toàn chuỗi — không thể xoá.");
        }
        if (repo.delete(maDV) == 0) {
            throw BusinessException.notFound("dịch vụ", maDV);
        }
        return DeleteResponse.hard("Đã xoá dịch vụ " + maDV + " tại S0 — Logical Replication sẽ xoá bản sao ở các chi nhánh.");
    }

    /** @return số lần sử dụng theo mã dịch vụ, hoặc null nếu không đọc được view toàn cục */
    private Map<String, Long> usageAcrossChain() {
        try {
            return repo.countSuDungByDichVu();
        } catch (DataAccessException e) {
            return null;
        }
    }

    private static DichVu toEntity(String maDV, DichVuRequest r) {
        return new DichVu(maDV, r.tenDV().trim(), Codes.blankToNull(r.loaiDV()), r.donViTinh().trim(), r.donGia());
    }
}
