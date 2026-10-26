package vn.antam.clinic.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.response.BenhNhanResponse;
import vn.antam.clinic.dto.response.ChiPhiResponse;
import vn.antam.clinic.dto.response.HoSoBenhNhanResponse;
import vn.antam.clinic.dto.response.LuotHoSoResponse;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BenhNhanRepository;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.HoSoBenhNhanRepository;

/**
 * F4 — hồ sơ bệnh nhân tại chi nhánh đang chọn. Gọi khi SiteContext = S0 thì thành hồ sơ toàn chuỗi (các truy vấn chạy
 * trên view toàn cục cùng tên).
 */
@Service
public class HoSoBenhNhanService {

    private final BenhNhanRepository benhNhanRepo;
    private final HoSoBenhNhanRepository hoSoRepo;
    private final ChanDoanDieuTriRepository dotRepo;

    public HoSoBenhNhanService(BenhNhanRepository benhNhanRepo, HoSoBenhNhanRepository hoSoRepo,
                               ChanDoanDieuTriRepository dotRepo) {
        this.benhNhanRepo = benhNhanRepo;
        this.hoSoRepo = hoSoRepo;
        this.dotRepo = dotRepo;
    }

    /** Cả 4 truy vấn chạy trong cùng một transaction chỉ đọc nên nhìn thấy cùng một trạng thái dữ liệu. */
    @Transactional(readOnly = true)
    public HoSoBenhNhanResponse hoSo(String maBN) {
        BenhNhanResponse bn = benhNhanRepo.findById(maBN)
                .orElseThrow(() -> BusinessException.notFound("bệnh nhân", maBN));
        Map<String, List<ChiPhiResponse>> chiPhi = hoSoRepo.findChiPhi(maBN).stream()
                .collect(Collectors.groupingBy(ChiPhiResponse::maLuot));
        List<LuotHoSoResponse> luot = hoSoRepo.findLuot(maBN).stream()
                .map(l -> l.withChiTiet(chiPhi.getOrDefault(l.maLuot(), List.of())))
                .toList();
        BigDecimal tong = luot.stream().map(LuotHoSoResponse::tongTien).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HoSoBenhNhanResponse(bn, dotRepo.findByBenhNhan(maBN), luot, tong);
    }
}
