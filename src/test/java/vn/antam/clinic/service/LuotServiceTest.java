package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.dto.response.LuotChiTietResponse;
import vn.antam.clinic.entity.ChanDoanDieuTri;
import vn.antam.clinic.entity.LuotKhamChua;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.LuotRepository;

@ExtendWith(MockitoExtension.class)
class LuotServiceTest {

    static final LocalDateTime TRONG_THANG = YearMonth.now().atDay(1).atTime(10, 0);

    @Mock LuotRepository luotRepo;
    @Mock ChanDoanDieuTriRepository dotRepo;

    LuotService service;

    @BeforeEach
    void setUp() {
        service = new LuotService(luotRepo, dotRepo);
    }

    @Test
    void search_inclusiveEndDate_passesStartOfNextDay() {
        when(luotRepo.search(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        service.search(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "KHAM", " ", null, " BN01 ");

        verify(luotRepo).search(LocalDate.of(2026, 10, 1).atStartOfDay(), LocalDate.of(2026, 11, 1).atStartOfDay(),
                "KHAM", null, null, "BN01", LuotService.GIOI_HAN);
    }

    @Test
    void search_unknownLoai_throwsBadParam() {
        assertThatThrownBy(() -> service.search(null, null, "XYZ", null, null, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void delete_lastTreatmentThatClosedDot_reopensDot() {
        when(luotRepo.findById("C-2")).thenReturn(Optional.of(LuotFixtures.luot("C-2", LuotKhamChua.LOAI_CHUA, TRONG_THANG, "CDDT-1", 2)));
        when(luotRepo.findLanChuaCuoi("CDDT-1")).thenReturn(Optional.of("C-2"));
        DotDieuTriResponse closed = closedDot(TRONG_THANG);
        when(dotRepo.findById("CDDT-1")).thenReturn(Optional.of(closed));

        DeleteResponse result = service.delete("C-2");

        verify(luotRepo).delete("C-2");
        verify(dotRepo).updateTienTrinh("CDDT-1", 3, ChanDoanDieuTri.DANG_DIEU_TRI, null);
        assertThat(result.message()).contains("mở lại");
    }

    @Test
    void delete_treatmentThatIsNotLast_throwsConflict() {
        when(luotRepo.findById("C-1")).thenReturn(Optional.of(LuotFixtures.luot("C-1", LuotKhamChua.LOAI_CHUA, TRONG_THANG, "CDDT-1", 1)));
        when(luotRepo.findLanChuaCuoi("CDDT-1")).thenReturn(Optional.of("C-2"));

        assertThatThrownBy(() -> service.delete("C-1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("CANNOT_DELETE"));
        verify(luotRepo, never()).delete(anyString());
    }

    @Test
    void delete_examWhoseDotHasTreatments_throwsConflict() {
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, TRONG_THANG, null, null)));
        when(luotRepo.countLanChuaCuaLuotKham("K-1")).thenReturn(2);

        assertThatThrownBy(() -> service.delete("K-1")).isInstanceOf(BusinessException.class).hasMessageContaining("2 lần chữa");
    }

    @Test
    void delete_visitOfPreviousMonth_throwsConflict() {
        LocalDateTime thangTruoc = TRONG_THANG.minusMonths(1);
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, thangTruoc, null, null)));

        assertThatThrownBy(() -> service.delete("K-1")).isInstanceOf(BusinessException.class).hasMessageContaining("tháng hiện tại");
    }

    @Test
    void get_exam_listsDotsOpenedByThisVisitAndDeletable() {
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, TRONG_THANG, null, null)));
        when(dotRepo.findByLuotKham("K-1")).thenReturn(List.of(closedDot(TRONG_THANG)));
        when(luotRepo.countLanChuaCuaLuotKham("K-1")).thenReturn(0);

        LuotChiTietResponse result = service.get("K-1");

        assertThat(result.dotDieuTri()).hasSize(1);
        assertThat(result.xoaDuoc()).isTrue();
    }

    private static DotDieuTriResponse closedDot(LocalDateTime end) {
        DotDieuTriResponse d = LuotFixtures.dot("CDDT-1", "B01", "CN1", ChanDoanDieuTri.DA_KHOI, end);
        return new DotDieuTriResponse(d.maCDDT(), d.maBenh(), d.tenBenh(), d.maICD10(), d.mucDo(), d.soLanChuaCanThiet(),
                d.soLanDaChua(), d.trangThai(), d.ngayBatDau(), end, d.maLuotKham(), d.maBN(), d.tenBN(), d.maBS(),
                d.tenBS(), d.maKhoa(), d.tenKhoa(), d.maCn(), d.lanChuaGanNhat(), d.luotGanNhat(), null, false);
    }
}
