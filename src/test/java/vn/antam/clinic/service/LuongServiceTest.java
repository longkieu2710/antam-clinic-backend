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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.dto.response.BangLuongResponse;
import vn.antam.clinic.dto.response.LuongNhanVienResponse;
import vn.antam.clinic.entity.LuongCoSo;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.LuongCoSoRepository;
import vn.antam.clinic.repository.LuongRepository;

@ExtendWith(MockitoExtension.class)
class LuongServiceTest {

    @Mock
    LuongRepository luongRepo;

    @Mock
    LuongCoSoRepository luongCoSoRepo;

    LuongService service;

    @BeforeEach
    void setUp() {
        service = new LuongService(luongRepo, luongCoSoRepo);
    }

    @Test
    void bangLuong_usesBaseSalaryInEffectAtEndOfMonthAndSumsTotal() {
        LuongCoSo coSo = new LuongCoSo(LocalDate.of(2024, 7, 1), new BigDecimal("2340000"), null);
        when(luongCoSoRepo.findApDungTai(LocalDate.of(2026, 9, 30))).thenReturn(Optional.of(coSo));
        when(luongRepo.tinhLuong(LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0),
                coSo.mucLuong(), LuongService.THUONG_MOI_DOT_KHOI, LuongService.THUONG_MOI_LAN_HO_TRO))
                .thenReturn(List.of(row("BS-CN1-01", "9564400"), row("YT-CN1-01", "5875600")));

        BangLuongResponse result = service.bangLuong(YearMonth.of(2026, 9));

        assertThat(result.thang()).isEqualTo("2026-09");
        assertThat(result.mucLuongCoSo()).isEqualByComparingTo("2340000");
        assertThat(result.tongQuyLuong()).isEqualByComparingTo("15440000");
        assertThat(result.nhanVien()).hasSize(2);
    }

    @Test
    void bangLuong_noBaseSalaryYet_throws422WithoutComputing() {
        when(luongCoSoRepo.findApDungTai(LocalDate.of(2020, 1, 31))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bangLuong(YearMonth.of(2020, 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(((BusinessException) e).getCode()).isEqualTo("NO_BASE_SALARY");
                });
        verify(luongRepo, never()).tinhLuong(any(), any(), any(), any(), any());
    }

    private static LuongNhanVienResponse row(String maNV, String tong) {
        return new LuongNhanVienResponse(maNV, "NV " + maNV, maNV.substring(0, 2), "CN1-TMH", "Khoa TMH",
                "Đang làm việc", new BigDecimal("2.34"), new BigDecimal("5475600"), 0, 0, BigDecimal.ZERO,
                new BigDecimal(tong));
    }
}
