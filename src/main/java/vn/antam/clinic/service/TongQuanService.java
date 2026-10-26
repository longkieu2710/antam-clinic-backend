package vn.antam.clinic.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.dto.response.DoanhThuResponse;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.dto.response.TongQuanResponse;
import vn.antam.clinic.entity.LuotKhamChua;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.LuotRepository;
import vn.antam.clinic.repository.TongQuanRepository;

/** Tổng quan chi nhánh: lượt hôm nay, đợt đang điều trị, doanh thu tháng, lịch hẹn, đợt quá hạn (BR12), bệnh nhiều nhất. */
@Service
public class TongQuanService {

    static final int SO_LUOT_GAN_DAY = 8;
    static final int SO_LICH_HEN = 8;
    static final int SO_BENH_TOP = 5;

    private final TongQuanRepository repo;
    private final LuotRepository luotRepo;
    private final ChanDoanDieuTriRepository dotRepo;
    private final DoanhThuService doanhThuService;
    private final ThongKeBenhService thongKeBenhService;

    public TongQuanService(TongQuanRepository repo, LuotRepository luotRepo, ChanDoanDieuTriRepository dotRepo,
                           DoanhThuService doanhThuService, ThongKeBenhService thongKeBenhService) {
        this.repo = repo;
        this.luotRepo = luotRepo;
        this.dotRepo = dotRepo;
        this.doanhThuService = doanhThuService;
        this.thongKeBenhService = thongKeBenhService;
    }

    @Transactional(readOnly = true)
    public TongQuanResponse tongQuan() {
        LocalDate today = LocalDate.now();
        var homNay = today.atStartOfDay();
        var ngayMai = today.plusDays(1).atStartOfDay();
        YearMonth thang = YearMonth.from(today);
        DoanhThuResponse doanhThu = doanhThuService.doanhThu(thang.atDay(1), today);
        List<DotDieuTriResponse> dangDieuTri = dotRepo.findAllDangDieuTri();
        List<DotDieuTriResponse> lichHen = dangDieuTri.stream()
                .filter(d -> d.ngayHenTaiKham() != null && !d.ngayHenTaiKham().isBefore(today))
                .limit(SO_LICH_HEN)
                .toList();
        List<DotDieuTriResponse> quaHan = dangDieuTri.stream().filter(DotDieuTriResponse::quaHan).toList();
        return new TongQuanResponse(today,
                repo.countLuot(LuotKhamChua.LOAI_KHAM, homNay, ngayMai),
                repo.countLuot(LuotKhamChua.LOAI_CHUA, homNay, ngayMai),
                dangDieuTri.size(),
                doanhThu.soLuot(),
                doanhThu.tongDoanhThu(),
                repo.countBenhNhanMoi(thang.atDay(1).atStartOfDay(), ngayMai),
                luotRepo.search(null, null, null, null, null, null, SO_LUOT_GAN_DAY),
                lichHen,
                quaHan,
                thongKeBenhService.thongKe(thang).benh().stream().limit(SO_BENH_TOP).toList());
    }
}
