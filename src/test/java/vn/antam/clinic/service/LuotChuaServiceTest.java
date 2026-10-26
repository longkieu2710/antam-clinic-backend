package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.dto.request.LuotChuaRequest;
import vn.antam.clinic.dto.request.NgungDieuTriRequest;
import vn.antam.clinic.dto.response.TaoLuotResponse;
import vn.antam.clinic.entity.ChanDoanDieuTri;
import vn.antam.clinic.entity.LanChua;
import vn.antam.clinic.entity.LanChuaHinhThuc;
import vn.antam.clinic.entity.LuotKhamChua;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BacSiRepository;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.LuotRepository;
import vn.antam.clinic.site.SiteContext;

@ExtendWith(MockitoExtension.class)
class LuotChuaServiceTest {

    static final LocalDateTime LUOT_TRUOC = LocalDateTime.of(2026, 10, 20, 9, 0);
    static final ChiTietLuotService.ChiTiet RONG = new ChiTietLuotService.ChiTiet(List.of(), List.of(), List.of(), List.of());

    @Mock LuotRepository luotRepo;
    @Mock ChanDoanDieuTriRepository dotRepo;
    @Mock BacSiRepository bacSiRepo;
    @Mock ChiTietLuotService chiTietService;

    LuotChuaService service;

    @BeforeEach
    void setUp() {
        service = new LuotChuaService(luotRepo, dotRepo, bacSiRepo, chiTietService, LuotFixtures.txManager());
        SiteContext.set("S1");
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
    }

    @Test
    void create_usesDoctorAndDepartmentOfDotAndNextLanThu() {
        stubOpenDot();
        when(dotRepo.countLanChua("CDDT-CN1-001")).thenReturn(1);
        when(luotRepo.findMaLuotByPrefix("C-CN1-TMH-BS-CN1-01-261022-")).thenReturn(List.of());

        TaoLuotResponse result = service.create(request("Đỡ", null));

        assertThat(result.maLuot()).isEqualTo("C-CN1-TMH-BS-CN1-01-261022-001");
        assertThat(result.lanThu()).isEqualTo(2);
        assertThat(result.ketThucDot()).isFalse();
        ArgumentCaptor<LuotKhamChua> luot = ArgumentCaptor.forClass(LuotKhamChua.class);
        verify(luotRepo).insert(luot.capture());
        assertThat(luot.getValue().maBS()).isEqualTo("BS-CN1-01");
        assertThat(luot.getValue().maKhoa()).isEqualTo("CN1-TMH");
        assertThat(luot.getValue().loaiLuot()).isEqualTo(LuotKhamChua.LOAI_CHUA);
        verify(luotRepo, times(2)).insert(any(LanChuaHinhThuc.class));
        verify(dotRepo).updateTienTrinh("CDDT-CN1-001", 3, ChanDoanDieuTri.DANG_DIEU_TRI, null);
    }

    @Test
    void create_khoi_closesDotAtVisitTimeAndDropsAppointment() {
        stubOpenDot();
        when(dotRepo.countLanChua("CDDT-CN1-001")).thenReturn(2);
        when(luotRepo.findMaLuotByPrefix(anyString())).thenReturn(List.of());

        TaoLuotResponse result = service.create(request(LanChua.KHOI, 3));

        assertThat(result.ketThucDot()).isTrue();
        ArgumentCaptor<LuotKhamChua> luot = ArgumentCaptor.forClass(LuotKhamChua.class);
        verify(luotRepo).insert(luot.capture());
        assertThat(luot.getValue().ngayHenTaiKham()).isNull();
        verify(dotRepo).updateTienTrinh("CDDT-CN1-001", 3, ChanDoanDieuTri.DA_KHOI, LUOT_TRUOC.plusDays(2));
    }

    @Test
    void create_closedDot_throwsBR3Conflict() {
        when(dotRepo.findById("CDDT-CN1-001")).thenReturn(Optional.of(
                LuotFixtures.dot("CDDT-CN1-001", "B01", "CN1", ChanDoanDieuTri.DA_KHOI, LUOT_TRUOC)));

        assertThatThrownBy(() -> service.create(request("Đỡ", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("BR3");
                });
        verify(luotRepo, never()).insert(any(LuotKhamChua.class));
    }

    @Test
    void create_notAfterLastVisitOfDot_throwsBR3() {
        when(dotRepo.findById("CDDT-CN1-001")).thenReturn(Optional.of(
                LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", LUOT_TRUOC.plusDays(2))));

        assertThatThrownBy(() -> service.create(request("Đỡ", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCode()).isEqualTo("BR3");
                });
    }

    @Test
    void create_doctorOfDotInactive_throwsBR2() {
        when(dotRepo.findById("CDDT-CN1-001")).thenReturn(Optional.of(
                LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", LUOT_TRUOC)));
        when(bacSiRepo.findById("BS-CN1-01")).thenReturn(Optional.of(
                LuotFixtures.bacSi("BS-CN1-01", "CN1-TMH", NhanVien.NGUNG_HOAT_DONG)));

        assertThatThrownBy(() -> service.create(request("Đỡ", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("BR2"));
    }

    @Test
    void create_unknownTinhTrang_throwsValidationBeforeDb() {
        assertThatThrownBy(() -> service.create(request("Tốt", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(dotRepo, luotRepo);
    }

    @Test
    void ngungDieuTri_openDot_setsStoppedWithEndTime() {
        when(dotRepo.findById("CDDT-CN1-001")).thenReturn(Optional.of(
                LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", LUOT_TRUOC)));

        service.ngungDieuTri("CDDT-CN1-001", new NgungDieuTriRequest(LUOT_TRUOC.plusDays(31), null));

        verify(dotRepo).updateTienTrinh("CDDT-CN1-001", 3, ChanDoanDieuTri.NGUNG_DIEU_TRI, LUOT_TRUOC.plusDays(31));
    }

    @Test
    void ngungDieuTri_alreadyEnded_throwsConflict() {
        when(dotRepo.findById("CDDT-CN1-001")).thenReturn(Optional.of(
                LuotFixtures.dot("CDDT-CN1-001", "B01", "CN1", ChanDoanDieuTri.NGUNG_DIEU_TRI, LUOT_TRUOC)));

        assertThatThrownBy(() -> service.ngungDieuTri("CDDT-CN1-001", null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("DOT_DA_KET_THUC"));
        verify(dotRepo, never()).updateTienTrinh(anyString(), eq(3), anyString(), any());
    }

    private void stubOpenDot() {
        when(dotRepo.findById("CDDT-CN1-001")).thenReturn(Optional.of(
                LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", LUOT_TRUOC)));
        when(bacSiRepo.findById("BS-CN1-01")).thenReturn(Optional.of(
                LuotFixtures.bacSi("BS-CN1-01", "CN1-TMH", NhanVien.DANG_LAM_VIEC)));
        when(chiTietService.kiemTra(any(), eq(true))).thenReturn(RONG);
    }

    private static LuotChuaRequest request(String tinhTrang, Integer soLan) {
        return new LuotChuaRequest("CDDT-CN1-001", LUOT_TRUOC.plusDays(2), List.of("Khí dung", " Tiêm "), "Đỡ đau",
                tinhTrang, soLan, LUOT_TRUOC.toLocalDate().plusDays(7), null, null, null, null, null);
    }
}
