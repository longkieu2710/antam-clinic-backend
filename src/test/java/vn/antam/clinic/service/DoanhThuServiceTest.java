package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import vn.antam.clinic.dto.response.DoanhThuThangResponse;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.DoanhThuRepository;

@ExtendWith(MockitoExtension.class)
class DoanhThuServiceTest {

    @Mock
    DoanhThuRepository repo;

    DoanhThuService service;

    @BeforeEach
    void setUp() {
        service = new DoanhThuService(repo);
    }

    @Test
    void doanhThu_rangeIncludesLastDayAndSumsMonths() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        when(repo.theoThang(start, end)).thenReturn(List.of(
                month("2026-08", 2, "150000", "250000", "125000", "100000"),
                month("2026-09", 5, "150000", "370000", "222000", "100000")));
        when(repo.chiTiet(start, end)).thenReturn(List.of());

        DoanhThuResponse result = service.doanhThu(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));

        assertThat(result.soLuot()).isEqualTo(7);
        assertThat(result.tienKham()).isEqualByComparingTo("300000");
        assertThat(result.tienThuoc()).isEqualByComparingTo("347000");
        assertThat(result.tongDoanhThu()).isEqualByComparingTo("1467000");
    }

    @Test
    void doanhThu_noDates_defaultsToCurrentMonth() {
        YearMonth now = YearMonth.now();
        when(repo.theoThang(now.atDay(1).atStartOfDay(), now.plusMonths(1).atDay(1).atStartOfDay())).thenReturn(List.of());
        when(repo.chiTiet(now.atDay(1).atStartOfDay(), now.plusMonths(1).atDay(1).atStartOfDay())).thenReturn(List.of());

        DoanhThuResponse result = service.doanhThu(null, null);

        assertThat(result.tu()).isEqualTo(now.atDay(1));
        assertThat(result.den()).isEqualTo(now.atEndOfMonth());
        assertThat(result.tongDoanhThu()).isEqualByComparingTo("0");
    }

    @Test
    void doanhThu_endBeforeStart_throwsBadRange() {
        assertThatThrownBy(() -> service.doanhThu(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("BAD_RANGE");
        verify(repo, never()).theoThang(any(), any());
    }

    private static DoanhThuThangResponse month(String thang, long soLuot, String kham, String dv, String thuoc, String tb) {
        BigDecimal k = new BigDecimal(kham), d = new BigDecimal(dv), t = new BigDecimal(thuoc), b = new BigDecimal(tb);
        return new DoanhThuThangResponse(thang, soLuot, k, d, t, b, k.add(d).add(t).add(b));
    }
}
