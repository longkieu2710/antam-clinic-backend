package vn.antam.clinic.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.ChiNhanhRequest;
import vn.antam.clinic.dto.response.ChiNhanhResponse;
import vn.antam.clinic.entity.ChiNhanh;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.ChiNhanhRepository;

/**
 * Chi nhánh — danh mục chung, đọc/ghi tại S0 (Logical Replication đẩy xuống S1..S3).
 * Chỉ cho sửa thông tin; không thêm / xoá / đổi mã (FDW và view toàn cục cố định 3 chi nhánh).
 */
@Service
public class ChiNhanhService {

    private final ChiNhanhRepository repo;
    private final AppProperties props;

    public ChiNhanhService(ChiNhanhRepository repo, AppProperties props) {
        this.repo = repo;
        this.props = props;
    }

    /**
     * Mọi trạm chi nhánh trong cấu hình, ghép với bảng CHI_NHANH. Không chạy trong transaction:
     * transaction lấy kết nối trước khi vào hàm, S0 tắt sẽ lỗi ngay thay vì rơi vào nhánh dự phòng bên dưới.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ChiNhanhResponse> list() {
        Map<String, ChiNhanh> rows;
        try {
            rows = repo.findAll().stream().collect(Collectors.toMap(ChiNhanh::maCn, Function.identity()));
        } catch (DataAccessException e) {
            rows = Map.of(); // S0 đang tắt: vẫn trả danh sách trạm từ cấu hình để FE chọn được chi nhánh
        }
        Map<String, ChiNhanh> db = rows;
        return props.branchSites().stream().map(siteId -> {
            AppProperties.Site site = props.sites().get(siteId);
            return ChiNhanhResponse.from(siteId, site, site.maCn() == null ? null : db.get(site.maCn()));
        }).toList();
    }

    @Transactional
    public ChiNhanhResponse update(String maCn, ChiNhanhRequest req) {
        ChiNhanh cn = new ChiNhanh(maCn, req.tenCn().trim(), req.diaChi(), req.soDienThoai());
        if (repo.update(cn) == 0) {
            throw BusinessException.notFound("chi nhánh", maCn);
        }
        String siteId = props.branchSites().stream()
                .filter(id -> maCn.equals(props.sites().get(id).maCn()))
                .findFirst()
                .orElse(null);
        return ChiNhanhResponse.from(siteId, siteId == null ? null : props.sites().get(siteId), cn);
    }
}
