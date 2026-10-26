package vn.antam.clinic.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.KhoaRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.KhoaResponse;
import vn.antam.clinic.entity.Khoa;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.KhoaRepository;
import vn.antam.clinic.site.SiteContext;
import vn.antam.clinic.util.Codes;

/** Khoa của chi nhánh đang chọn (header X-Site). MaCN của khoa luôn là chi nhánh đang chọn. */
@Service
public class KhoaService {

    private final KhoaRepository repo;
    private final AppProperties props;

    public KhoaService(KhoaRepository repo, AppProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<KhoaResponse> list() {
        return repo.findAllWithSoNhanVien();
    }

    @Transactional(readOnly = true)
    public KhoaResponse get(String maKhoa) {
        return repo.findById(maKhoa).orElseThrow(() -> BusinessException.notFound("khoa", maKhoa));
    }

    @Transactional
    public KhoaResponse create(KhoaRequest r) {
        String maCn = currentMaCn();
        String maKhoa = Codes.blankToNull(r.maKhoa());
        if (maKhoa == null) {
            maKhoa = Codes.next(maCn + "-K", repo.findAllMaKhoa(), 2);
        }
        repo.insert(new Khoa(maKhoa.toUpperCase(), r.tenKhoa().trim(), Codes.blankToNull(r.moTa()), maCn));
        return get(maKhoa.toUpperCase());
    }

    @Transactional
    public KhoaResponse update(String maKhoa, KhoaRequest r) {
        if (repo.update(new Khoa(maKhoa, r.tenKhoa().trim(), Codes.blankToNull(r.moTa()), currentMaCn())) == 0) {
            throw BusinessException.notFound("khoa", maKhoa);
        }
        return get(maKhoa);
    }

    /** Khoa còn nhân viên hoặc đã có lượt khám/chữa thì không xoá (khoá ngoại ON DELETE RESTRICT). */
    @Transactional
    public DeleteResponse delete(String maKhoa) {
        KhoaResponse k = get(maKhoa);
        if (k.soNhanVien() > 0 || k.soLuot() > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "IN_USE", k.tenKhoa() + " còn " + k.soNhanVien()
                    + " nhân viên và " + k.soLuot() + " lượt khám/chữa — không xoá được.");
        }
        repo.delete(maKhoa);
        return DeleteResponse.hard("Đã xoá " + k.tenKhoa() + " (" + maKhoa + ").");
    }

    private String currentMaCn() {
        String site = SiteContext.get();
        return props.site(site).map(AppProperties.Site::maCn).orElse(site);
    }
}
