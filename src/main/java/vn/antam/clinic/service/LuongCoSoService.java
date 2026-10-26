package vn.antam.clinic.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.request.LuongCoSoRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.LuongCoSoResponse;
import vn.antam.clinic.entity.LuongCoSo;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.LuongCoSoRepository;
import vn.antam.clinic.util.Codes;

/**
 * Lương cơ sở — danh mục chung có lịch sử, ghi tại S0 ({@code /api/master/luong-co-so}).
 * Lương tháng = hệ số × mức có hiệu lực trong tháng, nên mức đã áp dụng là dữ liệu lịch sử: không sửa / xoá,
 * muốn đổi thì thêm mức mới với ngày áp dụng mới.
 */
@Service
public class LuongCoSoService {

    private static final DateTimeFormatter VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LuongCoSoRepository repo;

    public LuongCoSoService(LuongCoSoRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<LuongCoSoResponse> list() {
        return withTrangThai(repo.findAll(), LocalDate.now());
    }

    @Transactional
    public LuongCoSoResponse create(LuongCoSoRequest r) {
        LuongCoSo row = new LuongCoSo(r.ngayApDung(), r.mucLuong(), Codes.blankToNull(r.vanBanQuyDinh()));
        repo.insert(row);
        return find(row.ngayApDung());
    }

    @Transactional
    public LuongCoSoResponse update(LocalDate ngayApDung, LuongCoSoRequest r) {
        requireNotApplied(ngayApDung, "sửa");
        if (repo.update(new LuongCoSo(ngayApDung, r.mucLuong(), Codes.blankToNull(r.vanBanQuyDinh()))) == 0) {
            throw BusinessException.notFound("mức lương cơ sở", ngayApDung.format(VN));
        }
        return find(ngayApDung);
    }

    @Transactional
    public DeleteResponse delete(LocalDate ngayApDung) {
        requireNotApplied(ngayApDung, "xoá");
        if (repo.delete(ngayApDung) == 0) {
            throw BusinessException.notFound("mức lương cơ sở", ngayApDung.format(VN));
        }
        return DeleteResponse.hard("Đã xoá mức lương cơ sở áp dụng từ " + ngayApDung.format(VN) + ".");
    }

    /** Gắn trạng thái cho danh sách đã sắp xếp mới nhất trước. */
    static List<LuongCoSoResponse> withTrangThai(List<LuongCoSo> rowsNewestFirst, LocalDate today) {
        List<LuongCoSoResponse> out = new ArrayList<>();
        boolean currentFound = false;
        for (LuongCoSo row : rowsNewestFirst) {
            String trangThai;
            if (row.ngayApDung().isAfter(today)) {
                trangThai = LuongCoSoResponse.SAP_AP_DUNG;
            } else if (!currentFound) {
                trangThai = LuongCoSoResponse.DANG_AP_DUNG;
                currentFound = true;
            } else {
                trangThai = LuongCoSoResponse.DA_THAY_THE;
            }
            out.add(LuongCoSoResponse.from(row, trangThai));
        }
        return out;
    }

    private LuongCoSoResponse find(LocalDate ngayApDung) {
        return list().stream()
                .filter(x -> x.ngayApDung().equals(ngayApDung))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("mức lương cơ sở", ngayApDung.format(VN)));
    }

    private static void requireNotApplied(LocalDate ngayApDung, String action) {
        if (!ngayApDung.isAfter(LocalDate.now())) {
            throw new BusinessException(HttpStatus.CONFLICT, "HISTORY_LOCKED",
                    "Mức lương cơ sở áp dụng từ " + ngayApDung.format(VN) + " đã có hiệu lực (dùng để tính lương các tháng đã qua) "
                            + "nên không " + action + " được — hãy thêm mức mới với ngày áp dụng mới.");
        }
    }
}
