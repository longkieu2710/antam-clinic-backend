package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.antam.clinic.dto.request.LuongCoSoRequest;
import vn.antam.clinic.dto.response.LuongCoSoResponse;
import vn.antam.clinic.entity.LuongCoSo;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.LuongCoSoRepository;

@ExtendWith(MockitoExtension.class)
class LuongCoSoServiceTest {

    @Mock
    LuongCoSoRepository repo;

    LuongCoSoService service;

    @BeforeEach
    void setUp() {
        service = new LuongCoSoService(repo);
    }

    @Test
    void withTrangThai_labelsFutureCurrentAndReplaced() {
        LocalDate today = LocalDate.of(2026, 10, 23);
        List<LuongCoSoResponse> rows = LuongCoSoService.withTrangThai(List.of(
                row("2027-07-01"), row("2024-07-01"), row("2023-07-01")), today);

        assertThat(rows).extracting(LuongCoSoResponse::trangThai).containsExactly(
                LuongCoSoResponse.SAP_AP_DUNG, LuongCoSoResponse.DANG_AP_DUNG, LuongCoSoResponse.DA_THAY_THE);
    }

    @Test
    void update_alreadyApplied_throwsHistoryLocked() {
        LocalDate past = LocalDate.now().minusYears(1);

        assertThatThrownBy(() -> service.update(past, new LuongCoSoRequest(past, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("HISTORY_LOCKED");
        verify(repo, never()).update(any());
    }

    @Test
    void delete_appliesToday_throwsHistoryLocked() {
        assertThatThrownBy(() -> service.delete(LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("HISTORY_LOCKED");
    }

    @Test
    void delete_future_deletes() {
        LocalDate future = LocalDate.now().plusMonths(3);
        when(repo.delete(future)).thenReturn(1);

        assertThat(service.delete(future).mode()).isEqualTo("HARD");
    }

    private static LuongCoSo row(String date) {
        return new LuongCoSo(LocalDate.parse(date), new BigDecimal("2340000"), null);
    }
}
