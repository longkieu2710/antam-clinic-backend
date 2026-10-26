package vn.antam.clinic.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.ThietBiRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.ThietBiResponse;
import vn.antam.clinic.entity.ThietBi;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.ThietBiRepository;
import vn.antam.clinic.site.SiteContext;
import vn.antam.clinic.util.Codes;

/** Thiết bị y tế của chi nhánh đang chọn (header X-Site). */
@Service
public class ThietBiService {

    private final ThietBiRepository repo;
    private final AppProperties props;

    public ThietBiService(ThietBiRepository repo, AppProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<ThietBiResponse> search(String q) {
        return repo.search(q);
    }

    @Transactional(readOnly = true)
    public ThietBiResponse get(String maTB) {
        return repo.findById(maTB).orElseThrow(() -> BusinessException.notFound("thiết bị", maTB));
    }

    @Transactional
    public ThietBiResponse create(ThietBiRequest r) {
        String maCn = currentMaCn();
        String maTB = Codes.blankToNull(r.maTB());
        if (maTB == null) {
            maTB = Codes.next("TB-" + maCn + "-", repo.findAllMaTB(), 2);
        }
        repo.insert(toEntity(maTB, r, maCn));
        return get(maTB);
    }

    /** Đổi đơn giá không ảnh hưởng lượt cũ: SU_DUNG_TB lưu DonGiaApDung tại thời điểm dùng (BR7). */
    @Transactional
    public ThietBiResponse update(String maTB, ThietBiRequest r) {
        if (repo.update(toEntity(maTB, r, currentMaCn())) == 0) {
            throw BusinessException.notFound("thiết bị", maTB);
        }
        return get(maTB);
    }

    /** BR9: thiết bị đã dùng trong lượt khám/chữa thì không xoá mà chuyển "Ngừng hoạt động". */
    @Transactional
    public DeleteResponse delete(String maTB) {
        ThietBiResponse tb = get(maTB);
        if (tb.soLanSuDung() > 0) {
            repo.updateTinhTrang(maTB, ThietBi.NGUNG_HOAT_DONG);
            return DeleteResponse.soft(tb.tenTB() + " đã dùng " + tb.soLanSuDung()
                    + " lần trong lượt khám/chữa nên không xoá — đã chuyển trạng thái \"" + ThietBi.NGUNG_HOAT_DONG + "\".");
        }
        repo.delete(maTB);
        return DeleteResponse.hard("Đã xoá thiết bị " + tb.tenTB() + " (" + maTB + ").");
    }

    private String currentMaCn() {
        String site = SiteContext.get();
        return props.site(site).map(AppProperties.Site::maCn).orElse(site);
    }

    private static ThietBi toEntity(String maTB, ThietBiRequest r, String maCn) {
        String tinhTrang = Codes.blankToNull(r.tinhTrang());
        return new ThietBi(maTB, r.tenTB().trim(), Codes.blankToNull(r.loaiTB()), r.donGiaSuDung(), r.ngayMua(),
                tinhTrang != null ? tinhTrang : ThietBi.HOAT_DONG_TOT, maCn);
    }
}
