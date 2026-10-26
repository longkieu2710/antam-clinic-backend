package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.antam.clinic.dto.response.BenhNhanResponse;
import vn.antam.clinic.dto.response.ChiPhiResponse;
import vn.antam.clinic.dto.response.HoSoBenhNhanResponse;
import vn.antam.clinic.dto.response.LuotHoSoResponse;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BenhNhanRepository;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.HoSoBenhNhanRepository;

@ExtendWith(MockitoExtension.class)
class HoSoBenhNhanServiceTest {

    @Mock
    BenhNhanRepository benhNhanRepo;

    @Mock
    HoSoBenhNhanRepository hoSoRepo;

    @Mock
    ChanDoanDieuTriRepository dotRepo;

    HoSoBenhNhanService service;

    @BeforeEach
    void setUp() {
        service = new HoSoBenhNhanService(benhNhanRepo, hoSoRepo, dotRepo);
    }

    @Test
    void hoSo_attachesCostLinesToEachLuotAndSumsTotal() {
        when(benhNhanRepo.findById("BN01")).thenReturn(Optional.of(new BenhNhanResponse("BN01", "Nguyễn Văn An", null,
                "Nam", null, null, null, null, null, null, 2, null, 1)));
        when(hoSoRepo.findLuot("BN01")).thenReturn(List.of(luot("K-1", "340000"), luot("C-1", "100000")));
        when(hoSoRepo.findChiPhi("BN01")).thenReturn(List.of(
                cost("K-1", "THUOC", "90000"), cost("K-1", "DICH_VU", "250000"), cost("C-1", "THIET_BI", "100000")));
        when(dotRepo.findByBenhNhan("BN01")).thenReturn(List.of());

        HoSoBenhNhanResponse result = service.hoSo("BN01");

        assertThat(result.tongChiPhi()).isEqualByComparingTo("440000");
        assertThat(result.luot()).extracting(l -> l.chiTiet().size()).containsExactly(2, 1);
    }

    @Test
    void hoSo_unknownPatient_throwsNotFoundWithoutLoadingHistory() {
        when(benhNhanRepo.findById("XX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.hoSo("XX")).isInstanceOf(BusinessException.class).hasMessageContaining("'XX'");
        verify(hoSoRepo, never()).findLuot(anyString());
    }

    private static LuotHoSoResponse luot(String maLuot, String tong) {
        BigDecimal t = new BigDecimal(tong);
        return new LuotHoSoResponse(maLuot, LocalDateTime.of(2026, 9, 5, 8, 30), "KHAM", "CN1-TMH", "Khoa TMH", "CN1",
                "BS-CN1-01", "BS A", null, null, null, null, null, null, null, null, null, "Viêm họng", null, null,
                t, BigDecimal.ZERO, BigDecimal.ZERO, t, List.of());
    }

    private static ChiPhiResponse cost(String maLuot, String loai, String tien) {
        return new ChiPhiResponse(maLuot, loai, "X", "Khoản " + loai, null, 1, new BigDecimal(tien), new BigDecimal(tien));
    }
}
