package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.antam.clinic.dto.response.ThongKeBenhItemResponse;
import vn.antam.clinic.dto.response.ThongKeBenhResponse;
import vn.antam.clinic.repository.ThongKeBenhRepository;

@ExtendWith(MockitoExtension.class)
class ThongKeBenhServiceTest {

    @Mock
    ThongKeBenhRepository repo;

    ThongKeBenhService service;

    @BeforeEach
    void setUp() {
        service = new ThongKeBenhService(repo);
    }

    @Test
    void thongKe_givenMonth_queriesWholeMonthAndSumsCount() {
        LocalDateTime tu = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime den = LocalDateTime.of(2026, 10, 1, 0, 0);
        when(repo.thongKe(tu, den)).thenReturn(List.of(
                new ThongKeBenhItemResponse("B01", "Viêm họng", "J02", 2, 2),
                new ThongKeBenhItemResponse("B04", "Viêm dạ dày", "K29", 1, 1)));

        ThongKeBenhResponse result = service.thongKe(YearMonth.of(2026, 9));

        assertThat(result.thang()).isEqualTo("2026-09");
        assertThat(result.tongLanMac()).isEqualTo(3);
        assertThat(result.benh()).hasSize(2);
    }

    @Test
    void thongKe_nullMonth_usesCurrentMonth() {
        YearMonth now = YearMonth.now();
        when(repo.thongKe(now.atDay(1).atStartOfDay(), now.plusMonths(1).atDay(1).atStartOfDay())).thenReturn(List.of());

        ThongKeBenhResponse result = service.thongKe(null);

        assertThat(result.thang()).isEqualTo(now.toString());
        assertThat(result.tongLanMac()).isZero();
        verify(repo).thongKe(now.atDay(1).atStartOfDay(), now.plusMonths(1).atDay(1).atStartOfDay());
    }
}
