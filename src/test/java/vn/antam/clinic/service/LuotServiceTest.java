package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
import vn.antam.clinic.dto.request.SuaLuotChuaRequest;
import vn.antam.clinic.dto.request.SuaLuotKhamRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.dto.response.LuotChiTietResponse;
import vn.antam.clinic.dto.response.LuotResponse;
import vn.antam.clinic.entity.ChanDoanDieuTri;
import vn.antam.clinic.entity.LanChua;
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

    @Test
    void get_exam_listsTreatmentsOfItsDotsAndIsEditableWhileDotsAreOpen() {
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, TRONG_THANG, null, null)));
        when(dotRepo.findByLuotKham("K-1")).thenReturn(List.of(LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", TRONG_THANG)));
        when(luotRepo.findLuotChuaCuaLuotKham("K-1")).thenReturn(List.of(
                LuotFixtures.luot("C-1", LuotKhamChua.LOAI_CHUA, TRONG_THANG.plusDays(2), "CDDT-CN1-001", 1)));

        LuotChiTietResponse result = service.get("K-1");

        assertThat(result.luotChua()).extracting(LuotResponse::maLuot).containsExactly("C-1");
        assertThat(result.suaDuoc()).isTrue();
    }

    @Test
    void get_examWithClosedDot_isNotEditable() {
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, TRONG_THANG, null, null)));
        when(dotRepo.findByLuotKham("K-1")).thenReturn(List.of(
                LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", TRONG_THANG),
                LuotFixtures.dot("CDDT-CN1-002", "B02", "CN1", ChanDoanDieuTri.NGUNG_DIEU_TRI, TRONG_THANG)));

        LuotChiTietResponse result = service.get("K-1");

        assertThat(result.suaDuoc()).isFalse();
        assertThat(result.lyDoKhongSua()).contains("Bệnh B02").contains("ngừng điều trị");
    }

    @Test
    void suaLuotKham_dotStillOpen_updatesConclusionAndAppointment() {
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, TRONG_THANG, null, null)));
        when(dotRepo.findByLuotKham("K-1")).thenReturn(List.of(LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", TRONG_THANG)));

        service.suaLuotKham("K-1", new SuaLuotKhamRequest(" Viêm họng, tái khám sau 1 tuần ", TRONG_THANG.toLocalDate().plusDays(7)));

        verify(luotRepo).updateKetLuanKham("K-1", "Viêm họng, tái khám sau 1 tuần");
        verify(luotRepo).updateNgayHenTaiKham("K-1", TRONG_THANG.toLocalDate().plusDays(7));
    }

    @Test
    void suaLuotKham_blankConclusion_usesDiagnosedDiseaseNames() {
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, TRONG_THANG, null, null)));
        when(dotRepo.findByLuotKham("K-1")).thenReturn(List.of(LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", TRONG_THANG)));

        service.suaLuotKham("K-1", new SuaLuotKhamRequest("  ", null));

        verify(luotRepo).updateKetLuanKham("K-1", "Bệnh B01");
        verify(luotRepo).updateNgayHenTaiKham("K-1", null);
    }

    @Test
    void suaLuotKham_dotAlreadyCured_throwsConflictWithoutWriting() {
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, TRONG_THANG, null, null)));
        when(dotRepo.findByLuotKham("K-1")).thenReturn(List.of(
                LuotFixtures.dot("CDDT-CN1-001", "B01", "CN1", ChanDoanDieuTri.DA_KHOI, TRONG_THANG)));

        assertThatThrownBy(() -> service.suaLuotKham("K-1", new SuaLuotKhamRequest("Khỏi", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("DOT_DA_KET_THUC"));
        verify(luotRepo, never()).updateKetLuanKham(anyString(), anyString());
    }

    @Test
    void suaLuotKham_appointmentNotAfterVisit_throwsBadAppointment() {
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, TRONG_THANG, null, null)));
        when(dotRepo.findByLuotKham("K-1")).thenReturn(List.of());

        assertThatThrownBy(() -> service.suaLuotKham("K-1", new SuaLuotKhamRequest(null, TRONG_THANG.toLocalDate())))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("BAD_APPOINTMENT"));
    }

    @Test
    void suaLuotKham_treatmentVisit_throwsNotExam() {
        when(luotRepo.findById("C-1")).thenReturn(Optional.of(
                LuotFixtures.luot("C-1", LuotKhamChua.LOAI_CHUA, TRONG_THANG, "CDDT-CN1-001", 1)));

        assertThatThrownBy(() -> service.suaLuotKham("C-1", new SuaLuotKhamRequest("x", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_EXAM"));
    }

    @Test
    void get_treatmentOfClosedDot_isNotEditable() {
        when(luotRepo.findById("C-1")).thenReturn(Optional.of(
                LuotFixtures.luot("C-1", LuotKhamChua.LOAI_CHUA, TRONG_THANG, "CDDT-1", 1)));
        when(dotRepo.findById("CDDT-1")).thenReturn(Optional.of(closedDot(TRONG_THANG)));
        when(luotRepo.findLanChuaCuoi("CDDT-1")).thenReturn(Optional.of("C-1"));

        LuotChiTietResponse result = service.get("C-1");

        assertThat(result.suaDuoc()).isFalse();
        assertThat(result.lyDoKhongSua()).contains("đã khỏi");
    }

    @Test
    void suaLuotChua_notCured_updatesResultAppointmentAndPlannedCount() {
        stubTreatment("C-2", TRONG_THANG, "C-2");

        service.suaLuotChua("C-2", new SuaLuotChuaRequest("Đỡ", " Bớt đau ", 5, TRONG_THANG.toLocalDate().plusDays(3)));

        verify(luotRepo).updateKetQuaChua("C-2", "Đỡ", "Bớt đau");
        verify(luotRepo).updateNgayHenTaiKham("C-2", TRONG_THANG.toLocalDate().plusDays(3));
        verify(dotRepo).updateTienTrinh("CDDT-CN1-001", 5, ChanDoanDieuTri.DANG_DIEU_TRI, null);
    }

    @Test
    void suaLuotChua_khoiOnLastTreatment_closesDotAtVisitTimeAndDropsAppointment() {
        stubTreatment("C-2", TRONG_THANG, "C-2");

        service.suaLuotChua("C-2", new SuaLuotChuaRequest(LanChua.KHOI, null, null, TRONG_THANG.toLocalDate().plusDays(3)));

        verify(luotRepo).updateKetQuaChua("C-2", LanChua.KHOI, null);
        verify(luotRepo).updateNgayHenTaiKham("C-2", null);
        verify(dotRepo).updateTienTrinh("CDDT-CN1-001", 3, ChanDoanDieuTri.DA_KHOI, TRONG_THANG);
    }

    @Test
    void suaLuotChua_khoiWhenLaterTreatmentExists_throwsConflictWithoutWriting() {
        stubTreatment("C-1", TRONG_THANG, "C-2");

        assertThatThrownBy(() -> service.suaLuotChua("C-1", new SuaLuotChuaRequest(LanChua.KHOI, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("KHOI_KHONG_PHAI_LAN_CUOI");
                });
        verify(luotRepo, never()).updateKetQuaChua(anyString(), any(), any());
        verify(dotRepo, never()).updateTienTrinh(anyString(), anyInt(), anyString(), any());
    }

    @Test
    void suaLuotChua_khoiOnTreatmentOfPreviousMonth_throwsHistoryLocked() {
        stubTreatment("C-2", TRONG_THANG.minusMonths(1), "C-2");

        assertThatThrownBy(() -> service.suaLuotChua("C-2", new SuaLuotChuaRequest(LanChua.KHOI, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("HISTORY_LOCKED"));
        verify(luotRepo, never()).updateKetQuaChua(anyString(), any(), any());
    }

    @Test
    void suaLuotChua_notCuredOnTreatmentOfPreviousMonth_isAllowed() {
        stubTreatment("C-1", TRONG_THANG.minusMonths(1), "C-2");

        service.suaLuotChua("C-1", new SuaLuotChuaRequest("Không đổi", null, null, null));

        verify(luotRepo).updateKetQuaChua("C-1", "Không đổi", null);
    }

    @Test
    void suaLuotChua_dotAlreadyEnded_throwsConflict() {
        when(luotRepo.findById("C-2")).thenReturn(Optional.of(
                LuotFixtures.luot("C-2", LuotKhamChua.LOAI_CHUA, TRONG_THANG, "CDDT-1", 2)));
        when(dotRepo.findById("CDDT-1")).thenReturn(Optional.of(closedDot(TRONG_THANG)));

        assertThatThrownBy(() -> service.suaLuotChua("C-2", new SuaLuotChuaRequest("Đỡ", null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("DOT_DA_KET_THUC"));
        verify(luotRepo, never()).updateKetQuaChua(anyString(), any(), any());
    }

    @Test
    void suaLuotChua_unknownStatus_throwsValidationBeforeLoadingDot() {
        when(luotRepo.findById("C-2")).thenReturn(Optional.of(
                LuotFixtures.luot("C-2", LuotKhamChua.LOAI_CHUA, TRONG_THANG, "CDDT-CN1-001", 2)));

        assertThatThrownBy(() -> service.suaLuotChua("C-2", new SuaLuotChuaRequest("Tốt", null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(dotRepo, never()).findById(anyString());
    }

    @Test
    void suaLuotChua_appointmentNotAfterVisit_throwsBadAppointment() {
        stubTreatment("C-2", TRONG_THANG, "C-2");

        assertThatThrownBy(() -> service.suaLuotChua("C-2", new SuaLuotChuaRequest("Đỡ", null, null, TRONG_THANG.toLocalDate())))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("BAD_APPOINTMENT"));
    }

    @Test
    void suaLuotChua_examVisit_throwsNotTreatment() {
        when(luotRepo.findById("K-1")).thenReturn(Optional.of(LuotFixtures.luot("K-1", LuotKhamChua.LOAI_KHAM, TRONG_THANG, null, null)));

        assertThatThrownBy(() -> service.suaLuotChua("K-1", new SuaLuotChuaRequest("Đỡ", null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_TREATMENT"));
    }

    /** Lượt chữa {@code maLuot} của đợt đang điều trị CDDT-CN1-001 (3 lần dự kiến), lần chữa cuối của đợt là {@code cuoi}. */
    private void stubTreatment(String maLuot, LocalDateTime thoiGian, String cuoi) {
        when(luotRepo.findById(maLuot)).thenReturn(Optional.of(
                LuotFixtures.luot(maLuot, LuotKhamChua.LOAI_CHUA, thoiGian, "CDDT-CN1-001", 1)));
        when(dotRepo.findById("CDDT-CN1-001")).thenReturn(Optional.of(LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", thoiGian)));
        lenient().when(luotRepo.findLanChuaCuoi("CDDT-CN1-001")).thenReturn(Optional.of(cuoi));
    }

    private static DotDieuTriResponse closedDot(LocalDateTime end) {
        DotDieuTriResponse d = LuotFixtures.dot("CDDT-1", "B01", "CN1", ChanDoanDieuTri.DA_KHOI, end);
        return new DotDieuTriResponse(d.maCDDT(), d.maBenh(), d.tenBenh(), d.maICD10(), d.mucDo(), d.soLanChuaCanThiet(),
                d.soLanDaChua(), d.trangThai(), d.ngayBatDau(), end, d.maLuotKham(), d.maBN(), d.tenBN(), d.maBS(),
                d.tenBS(), d.maKhoa(), d.tenKhoa(), d.maCn(), d.lanChuaGanNhat(), d.luotGanNhat(), null, false);
    }
}
