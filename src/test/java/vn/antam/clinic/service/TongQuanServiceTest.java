package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.antam.clinic.dto.response.DoanhThuResponse;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.dto.response.ThongKeBenhItemResponse;
import vn.antam.clinic.dto.response.ThongKeBenhResponse;
import vn.antam.clinic.dto.response.TongQuanResponse;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.LuotRepository;
import vn.antam.clinic.repository.TongQuanRepository;

@ExtendWith(MockitoExtension.class)
class TongQuanServiceTest {

    @Mock TongQuanRepository repo;
    @Mock LuotRepository luotRepo;
    @Mock ChanDoanDieuTriRepository dotRepo;
    @Mock DoanhThuService doanhThuService;
    @Mock ThongKeBenhService thongKeBenhService;

    TongQuanService service;

    @BeforeEach
    void setUp() {
        service = new TongQuanService(repo, luotRepo, dotRepo, doanhThuService, thongKeBenhService);
    }

    @Test
    void tongQuan_keepsUpcomingAppointmentsAndOverdueDots() {
        LocalDate today = LocalDate.now();
        when(doanhThuService.doanhThu(YearMonth.now().atDay(1), today)).thenReturn(new DoanhThuResponse(today, today, 7,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("990000"), List.of(), List.of()));
        when(dotRepo.findAllDangDieuTri()).thenReturn(List.of(
                dot("D-hen", today.plusDays(2), false), dot("D-cu", today.minusDays(1), false), dot("D-qua-han", null, true)));
        when(repo.countLuot(anyString(), any(), any())).thenReturn(1L);
        when(luotRepo.search(null, null, null, null, null, null, TongQuanService.SO_LUOT_GAN_DAY)).thenReturn(List.of());
        when(thongKeBenhService.thongKe(YearMonth.now())).thenReturn(new ThongKeBenhResponse("x", 0, List.of(
                new ThongKeBenhItemResponse("B1", "a", null, 9, 1), new ThongKeBenhItemResponse("B2", "b", null, 8, 1),
                new ThongKeBenhItemResponse("B3", "c", null, 7, 1), new ThongKeBenhItemResponse("B4", "d", null, 6, 1),
                new ThongKeBenhItemResponse("B5", "e", null, 5, 1), new ThongKeBenhItemResponse("B6", "f", null, 4, 1))));

        TongQuanResponse result = service.tongQuan();

        assertThat(result.lichHen()).extracting(DotDieuTriResponse::maCDDT).containsExactly("D-hen");
        assertThat(result.quaHan()).extracting(DotDieuTriResponse::maCDDT).containsExactly("D-qua-han");
        assertThat(result.dotDangDieuTri()).isEqualTo(3);
        assertThat(result.topBenh()).hasSize(TongQuanService.SO_BENH_TOP);
        assertThat(result.doanhThuThang()).isEqualByComparingTo("990000");
    }

    private static DotDieuTriResponse dot(String ma, LocalDate hen, boolean quaHan) {
        DotDieuTriResponse d = LuotFixtures.dotDang(ma, "B01", "CN1", LocalDateTime.now().minusDays(quaHan ? 40 : 1));
        return new DotDieuTriResponse(d.maCDDT(), d.maBenh(), d.tenBenh(), d.maICD10(), d.mucDo(), d.soLanChuaCanThiet(),
                d.soLanDaChua(), d.trangThai(), d.ngayBatDau(), null, d.maLuotKham(), d.maBN(), d.tenBN(), d.maBS(), d.tenBS(),
                d.maKhoa(), d.tenKhoa(), d.maCn(), d.lanChuaGanNhat(), d.luotGanNhat(), hen, quaHan);
    }
}
