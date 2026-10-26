package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.dto.response.DoanhThuChiNhanhThangResponse;
import vn.antam.clinic.dto.response.DoanhThuResponse;
import vn.antam.clinic.dto.response.DoanhThuToanChuoiResponse;
import vn.antam.clinic.dto.response.SiteStatusResponse;
import vn.antam.clinic.dto.response.ThongKeBenhChiNhanhResponse;
import vn.antam.clinic.dto.response.ThongKeBenhItemResponse;
import vn.antam.clinic.dto.response.ThongKeBenhResponse;
import vn.antam.clinic.dto.response.ThongKeBenhToanChuoiResponse;
import vn.antam.clinic.entity.ChiNhanh;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.ChiNhanhRepository;
import vn.antam.clinic.repository.ToanChuoiRepository;

@ExtendWith(MockitoExtension.class)
class ToanChuoiServiceTest {

    @Mock ToanChuoiRepository repo;
    @Mock ChiNhanhRepository chiNhanhRepo;
    @Mock HoSoBenhNhanService hoSoService;
    @Mock DoanhThuService doanhThuService;
    @Mock ThongKeBenhService thongKeBenhService;
    @Mock SiteHealthService health;

    ToanChuoiService service;

    @BeforeEach
    void setUp() {
        service = new ToanChuoiService(repo, chiNhanhRepo, hoSoService, doanhThuService, thongKeBenhService, health);
    }

    @Test
    void doanhThu_sumsEachBranchAndComputesShare() {
        LocalDate tu = LocalDate.of(2026, 9, 1);
        LocalDate den = LocalDate.of(2026, 10, 31);
        when(doanhThuService.doanhThu(tu, den)).thenReturn(new DoanhThuResponse(tu, den, 4, money(0), money(0), money(0),
                money(0), money(1000), List.of(), List.of()));
        when(repo.doanhThuTheoChiNhanh(tu.atStartOfDay(), LocalDate.of(2026, 11, 1).atStartOfDay())).thenReturn(List.of(
                row("CN1", "2026-09", 2, 300), row("CN1", "2026-10", 1, 450), row("CN3", "2026-10", 1, 250)));
        when(chiNhanhRepo.findAll()).thenReturn(List.of(new ChiNhanh("CN1", "Cầu Giấy", null, null),
                new ChiNhanh("CN2", "Đống Đa", null, null), new ChiNhanh("CN3", "Ba Đình", null, null)));

        DoanhThuToanChuoiResponse result = service.doanhThu(tu, den);

        assertThat(result.chiNhanh()).extracting(c -> c.maCn() + "=" + c.tong().intValue() + "/" + c.soLuot() + "/" + c.tyTrong())
                .containsExactly("CN1=750/3/75.0", "CN2=0/0/0.0", "CN3=250/1/25.0");
    }

    @Test
    void thongKeBenh_spreadsCountsOverAllBranches() {
        YearMonth thang = YearMonth.of(2026, 9);
        when(thongKeBenhService.thongKe(thang)).thenReturn(new ThongKeBenhResponse("2026-09", 3,
                List.of(new ThongKeBenhItemResponse("B01", "Viêm họng", "J02", 3, 2))));
        when(repo.thongKeBenhTheoChiNhanh(any(), any())).thenReturn(List.of(
                new ThongKeBenhChiNhanhResponse("B01", "CN1", 2, 1), new ThongKeBenhChiNhanhResponse("B01", "CN3", 1, 1)));
        when(chiNhanhRepo.findAll()).thenReturn(List.of(new ChiNhanh("CN1", "Cầu Giấy", null, null),
                new ChiNhanh("CN2", "Đống Đa", null, null), new ChiNhanh("CN3", "Ba Đình", null, null)));

        ThongKeBenhToanChuoiResponse result = service.thongKeBenh(thang);

        assertThat(result.benh().get(0).theoChiNhanh()).containsEntry("CN1", 2L).containsEntry("CN2", 0L).containsEntry("CN3", 1L);
        assertThat(result.tongTheoChiNhanh()).containsEntry("CN1", 2L);
    }

    @Test
    void hoSo_branchDown_throws503NamingTheSite() {
        when(hoSoService.hoSo("BN01")).thenThrow(new DataAccessResourceFailureException("fdw"));
        when(health.checkAll()).thenReturn(List.of(
                new SiteStatusResponse("S0", "HUB", "Trung tâm", null, null, true, 1L, null, null, null),
                new SiteStatusResponse("S2", "BRANCH", "Đống Đa", "CN2", null, false, null, null, null, "refused")));

        assertThatThrownBy(() -> service.hoSo("BN01"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("S2 (CN2)");
                });
    }

    @Test
    void hoSo_dbErrorWhileAllSitesUp_rethrowsOriginal() {
        when(hoSoService.hoSo("BN01")).thenThrow(new DataAccessResourceFailureException("lỗi khác"));
        when(health.checkAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.hoSo("BN01")).isInstanceOf(DataAccessResourceFailureException.class);
    }

    private static DoanhThuChiNhanhThangResponse row(String cn, String thang, long soLuot, long tong) {
        return new DoanhThuChiNhanhThangResponse(cn, thang, soLuot, money(0), money(tong), money(0), money(0), money(tong));
    }

    private static BigDecimal money(long v) {
        return BigDecimal.valueOf(v);
    }
}
