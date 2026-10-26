package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.dto.request.ChanDoanRequest;
import vn.antam.clinic.dto.request.LuotKhamRequest;
import vn.antam.clinic.dto.response.KhoaResponse;
import vn.antam.clinic.dto.response.TaoLuotResponse;
import vn.antam.clinic.entity.Benh;
import vn.antam.clinic.entity.BenhNhan;
import vn.antam.clinic.entity.ChanDoanDieuTri;
import vn.antam.clinic.entity.LanKham;
import vn.antam.clinic.entity.LuotKhamChua;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BacSiRepository;
import vn.antam.clinic.repository.BenhNhanRepository;
import vn.antam.clinic.repository.BenhRepository;
import vn.antam.clinic.repository.ChanDoanDieuTriRepository;
import vn.antam.clinic.repository.KhoaRepository;
import vn.antam.clinic.repository.LuotRepository;
import vn.antam.clinic.repository.ToanChuoiRepository;
import vn.antam.clinic.site.SiteContext;

@ExtendWith(MockitoExtension.class)
class LuotKhamServiceTest {

    static final LocalDateTime THOI_GIAN = LocalDateTime.of(2026, 10, 23, 9, 30);
    static final ChiTietLuotService.ChiTiet RONG = new ChiTietLuotService.ChiTiet(List.of(), List.of(), List.of(), List.of());

    @Mock LuotRepository luotRepo;
    @Mock ChanDoanDieuTriRepository dotRepo;
    @Mock BenhNhanRepository benhNhanRepo;
    @Mock BacSiRepository bacSiRepo;
    @Mock KhoaRepository khoaRepo;
    @Mock BenhRepository benhRepo;
    @Mock ToanChuoiRepository toanChuoiRepo;
    @Mock ChiTietLuotService chiTietService;

    LuotKhamService service;

    @BeforeEach
    void setUp() {
        service = new LuotKhamService(luotRepo, dotRepo, benhNhanRepo, bacSiRepo, khoaRepo, benhRepo, toanChuoiRepo,
                chiTietService, LuotFixtures.props(), LuotFixtures.txManager());
        SiteContext.set("S1");
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
    }

    @Test
    void create_patientFromOtherBranch_copiesRecordThenSavesVisitAndOpensDot() {
        BenhNhan bn = new BenhNhan("BN02", "Trần Thị Bình", null, "Nữ", "001095006666", null, null, null, null, null);
        when(benhNhanRepo.exists("BN02")).thenReturn(false);
        when(toanChuoiRepo.findBenhNhan("BN02")).thenReturn(Optional.of(bn));
        when(dotRepo.findDangDieuTri("BN02")).thenReturn(List.of(
                LuotFixtures.dotDang("CDDT-CN2-001", "B03", "CN2", THOI_GIAN.minusDays(5))));
        stubDoctorAndCatalog();
        when(luotRepo.findMaLuotByPrefix("K-CN1-TMH-BS-CN1-01-261023-")).thenReturn(List.of("K-CN1-TMH-BS-CN1-01-261023-001"));
        when(dotRepo.findAllMaCDDT()).thenReturn(List.of("CDDT-CN1-001", "CDDT-CN1-009"));

        TaoLuotResponse result = service.create(request("BN02", List.of(new ChanDoanRequest("B01", "Nặng", 3)), null));

        assertThat(result.maLuot()).isEqualTo("K-CN1-TMH-BS-CN1-01-261023-002");
        assertThat(result.maCDDT()).containsExactly("CDDT-CN1-010");
        assertThat(result.daChepBenhNhan()).isTrue();
        assertThat(result.canhBao()).isEmpty();
        verify(benhNhanRepo).insert(bn);
        ArgumentCaptor<ChanDoanDieuTri> dot = ArgumentCaptor.forClass(ChanDoanDieuTri.class);
        verify(dotRepo).insert(dot.capture());
        assertThat(dot.getValue().trangThai()).isEqualTo(ChanDoanDieuTri.DANG_DIEU_TRI);
        assertThat(dot.getValue().maLuotKham()).isEqualTo(result.maLuot());
    }

    @Test
    void create_openDotOfSameDiseaseAtOtherBranch_throwsBR4WithoutWriting() {
        when(benhNhanRepo.exists("BN02")).thenReturn(true);
        when(dotRepo.findDangDieuTri("BN02")).thenReturn(List.of(
                LuotFixtures.dotDang("CDDT-CN2-001", "B03", "CN2", THOI_GIAN.minusDays(5))));

        assertThatThrownBy(() -> service.create(request("BN02", List.of(new ChanDoanRequest("B03", "Nhẹ", 1)), null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("BR4");
                    assertThat(e.getMessage()).contains("CN2");
                });
        verify(luotRepo, never()).insert(any(LuotKhamChua.class));
    }

    @Test
    void create_hubDown_warnsAndStillChecksBR4Locally() {
        when(benhNhanRepo.exists("BN01")).thenReturn(true);
        when(dotRepo.findDangDieuTri("BN01"))
                .thenThrow(new DataAccessResourceFailureException("S0 tắt"))
                .thenReturn(List.of());
        stubDoctorAndCatalog();
        when(luotRepo.findMaLuotByPrefix(anyString())).thenReturn(List.of());
        when(dotRepo.findAllMaCDDT()).thenReturn(List.of());

        TaoLuotResponse result = service.create(request("BN01", List.of(new ChanDoanRequest("B01", "Nhẹ", 1)), null));

        assertThat(result.canhBao()).singleElement().asString().contains("BR4");
        verify(dotRepo, times(2)).findDangDieuTri("BN01");
    }

    @Test
    void create_localOpenDotOfSameDisease_throwsBR4() {
        when(benhNhanRepo.exists("BN01")).thenReturn(true);
        when(dotRepo.findDangDieuTri("BN01")).thenReturn(List.of(
                LuotFixtures.dotDang("CDDT-CN1-001", "B01", "CN1", THOI_GIAN.minusDays(2))));
        stubDoctorAndCatalog();

        assertThatThrownBy(() -> service.create(request("BN01", List.of(new ChanDoanRequest("B01", "Nhẹ", 1)), null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("BR4"));
    }

    @Test
    void create_noDiagnosis_savesVisitWithoutDotAndDefaultConclusion() {
        when(benhNhanRepo.exists("BN01")).thenReturn(true);
        when(khoaRepo.findById("CN1-TMH")).thenReturn(Optional.of(new KhoaResponse("CN1-TMH", "Khoa TMH", null, "CN1", 2, 0)));
        when(bacSiRepo.findById("BS-CN1-01")).thenReturn(Optional.of(LuotFixtures.bacSi("BS-CN1-01", "CN1-TMH", NhanVien.DANG_LAM_VIEC)));
        when(chiTietService.kiemTra(any(), eq(false))).thenReturn(RONG);
        when(luotRepo.findMaLuotByPrefix(anyString())).thenReturn(List.of());

        TaoLuotResponse result = service.create(request("BN01", List.of(), null));

        assertThat(result.maCDDT()).isEmpty();
        ArgumentCaptor<LanKham> lanKham = ArgumentCaptor.forClass(LanKham.class);
        verify(luotRepo).insert(lanKham.capture());
        assertThat(lanKham.getValue().ketLuanKham()).isEqualTo("Không phát hiện bệnh");
        verify(dotRepo, never()).insert(any());
        verifyNoInteractions(toanChuoiRepo);
    }

    @Test
    void create_doctorOfOtherDepartment_throwsWrongDepartment() {
        when(benhNhanRepo.exists("BN01")).thenReturn(true);
        when(khoaRepo.findById("CN1-TMH")).thenReturn(Optional.of(new KhoaResponse("CN1-TMH", "Khoa TMH", null, "CN1", 2, 0)));
        when(bacSiRepo.findById("BS-CN1-01")).thenReturn(Optional.of(LuotFixtures.bacSi("BS-CN1-01", "CN1-NOI", NhanVien.DANG_LAM_VIEC)));

        assertThatThrownBy(() -> service.create(request("BN01", List.of(), null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WRONG_DEPARTMENT"));
    }

    @Test
    void create_inactiveDoctor_throwsStaffInactive() {
        when(benhNhanRepo.exists("BN01")).thenReturn(true);
        when(khoaRepo.findById("CN1-TMH")).thenReturn(Optional.of(new KhoaResponse("CN1-TMH", "Khoa TMH", null, "CN1", 2, 0)));
        when(bacSiRepo.findById("BS-CN1-01")).thenReturn(Optional.of(LuotFixtures.bacSi("BS-CN1-01", "CN1-TMH", NhanVien.NGUNG_HOAT_DONG)));

        assertThatThrownBy(() -> service.create(request("BN01", List.of(), null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("STAFF_INACTIVE"));
    }

    @Test
    void create_sameDiseaseTwiceInOneVisit_throwsBR1BeforeReadingDb() {
        List<ChanDoanRequest> cd = List.of(new ChanDoanRequest("B01", "Nhẹ", 1), new ChanDoanRequest("B01", "Nặng", 2));

        assertThatThrownBy(() -> service.create(request("BN01", cd, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("BR1");
                });
        verifyNoInteractions(benhNhanRepo, luotRepo);
    }

    @Test
    void create_appointmentNotAfterVisit_throwsBadAppointment() {
        assertThatThrownBy(() -> service.create(request("BN01", List.of(), THOI_GIAN.toLocalDate())))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("BAD_APPOINTMENT"));
    }

    @Test
    void create_futureTime_throwsFutureTime() {
        LuotKhamRequest r = new LuotKhamRequest("BN01", "CN1-TMH", "BS-CN1-01", LocalDateTime.now().plusDays(1),
                null, null, null, null, null, List.of(), null, null, null, null);

        assertThatThrownBy(() -> service.create(r))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("FUTURE_TIME"));
    }

    @Test
    void create_codeTakenByConcurrentSave_retriesWithNewTransaction() {
        when(benhNhanRepo.exists("BN01")).thenReturn(true);
        when(khoaRepo.findById("CN1-TMH")).thenReturn(Optional.of(new KhoaResponse("CN1-TMH", "Khoa TMH", null, "CN1", 2, 0)));
        when(bacSiRepo.findById("BS-CN1-01")).thenReturn(Optional.of(LuotFixtures.bacSi("BS-CN1-01", "CN1-TMH", NhanVien.DANG_LAM_VIEC)));
        when(chiTietService.kiemTra(any(), eq(false))).thenReturn(RONG);
        when(luotRepo.findMaLuotByPrefix(anyString())).thenReturn(List.of(), List.of("K-CN1-TMH-BS-CN1-01-261023-001"));
        doThrow(new DuplicateKeyException("trùng")).doNothing().when(luotRepo).insert(any(LuotKhamChua.class));

        TaoLuotResponse result = service.create(request("BN01", List.of(), null));

        assertThat(result.maLuot()).isEqualTo("K-CN1-TMH-BS-CN1-01-261023-002");
    }

    private void stubDoctorAndCatalog() {
        when(khoaRepo.findById("CN1-TMH")).thenReturn(Optional.of(new KhoaResponse("CN1-TMH", "Khoa TMH", null, "CN1", 2, 0)));
        when(bacSiRepo.findById("BS-CN1-01")).thenReturn(Optional.of(LuotFixtures.bacSi("BS-CN1-01", "CN1-TMH", NhanVien.DANG_LAM_VIEC)));
        when(benhRepo.search(null)).thenReturn(List.of(new Benh("B01", "Viêm họng cấp tính", "J02", null),
                new Benh("B03", "Sâu răng tủy", "K02.5", null)));
        lenient().when(chiTietService.kiemTra(any(), eq(false))).thenReturn(RONG);
    }

    private static LuotKhamRequest request(String maBN, List<ChanDoanRequest> chanDoan, LocalDate ngayHen) {
        return new LuotKhamRequest(maBN, "CN1-TMH", "BS-CN1-01", THOI_GIAN, "Đau họng", null, null, ngayHen, null,
                chanDoan, null, null, null, null);
    }
}
