package vn.antam.clinic.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.request.BenhRequest;
import vn.antam.clinic.dto.response.BenhResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.entity.Benh;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BenhRepository;
import vn.antam.clinic.util.Codes;

/**
 * Bệnh (ICD-10) — danh mục chung. Ghi tại S0 ({@code /api/master/benh}), Logical Replication tự đẩy xuống S1..S3;
 * chi nhánh chỉ đọc bản sao ({@code /api/benh}).
 */
@Service
public class BenhService {

    private final BenhRepository repo;

    public BenhService(BenhRepository repo) {
        this.repo = repo;
    }

    /** Danh sách ở S0 kèm số đợt điều trị toàn chuỗi; không chạy trong transaction (xem ThuocService.listMaster). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<BenhResponse> listMaster(String q) {
        List<Benh> rows = repo.search(q);
        Map<String, Long> usage = usageAcrossChain();
        return rows.stream()
                .map(b -> BenhResponse.from(b, usage == null ? null : usage.getOrDefault(b.maBenh(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BenhResponse> listReplica(String q) {
        return repo.search(q).stream().map(b -> BenhResponse.from(b, null)).toList();
    }

    @Transactional
    public BenhResponse create(BenhRequest r) {
        String ma = Codes.blankToNull(r.maBenh());
        if (ma == null) {
            ma = Codes.next("B", repo.findAllMaBenh(), 2);
        }
        Benh benh = toEntity(ma, r);
        repo.insert(benh);
        return BenhResponse.from(benh, 0L);
    }

    @Transactional
    public BenhResponse update(String maBenh, BenhRequest r) {
        Benh benh = toEntity(maBenh, r);
        if (repo.update(benh) == 0) {
            throw BusinessException.notFound("bệnh", maBenh);
        }
        return BenhResponse.from(benh, null);
    }

    /**
     * Chi nhánh không kiểm tra khoá ngoại với dữ liệu đồng bộ, nên trước khi xoá ở S0 phải tự kiểm tra bệnh đã được
     * chẩn đoán ở chi nhánh nào chưa (qua view CHAN_DOAN_DIEU_TRI toàn cục).
     */
    // Không bọc transaction: câu đếm qua postgres_fdw lỗi (một chi nhánh tắt) làm PostgreSQL huỷ transaction và Hikari
    // đóng kết nối → rollback lỗi đè mất thông báo 503. Câu DELETE ở S0 chỉ một lệnh nên tự nó đã nguyên tử.
    public DeleteResponse delete(String maBenh) {
        Map<String, Long> usage = usageAcrossChain();
        if (usage == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CHECK_UNAVAILABLE",
                    "Không đọc được chẩn đoán ở các chi nhánh (một trạm đang tắt) nên tạm không cho xoá.");
        }
        long used = usage.getOrDefault(maBenh, 0L);
        if (used > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "IN_USE",
                    "Bệnh " + maBenh + " đã được chẩn đoán trong " + used + " đợt điều trị ở toàn chuỗi — không thể xoá.");
        }
        if (repo.delete(maBenh) == 0) {
            throw BusinessException.notFound("bệnh", maBenh);
        }
        return DeleteResponse.hard("Đã xoá bệnh " + maBenh + " tại S0 — Logical Replication sẽ xoá bản sao ở các chi nhánh.");
    }

    /** @return số đợt điều trị theo mã bệnh, hoặc null nếu không đọc được view toàn cục */
    private Map<String, Long> usageAcrossChain() {
        try {
            return repo.countChanDoanByBenh();
        } catch (DataAccessException e) {
            return null;
        }
    }

    private static Benh toEntity(String maBenh, BenhRequest r) {
        String icd = Codes.blankToNull(r.maICD10());
        return new Benh(maBenh, r.tenBenh().trim(), icd == null ? null : icd.toUpperCase(), Codes.blankToNull(r.moTa()));
    }
}
