package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.antam.clinic.dto.request.DichVuDungRequest;
import vn.antam.clinic.dto.request.LuotChuaRequest;
import vn.antam.clinic.dto.request.ThietBiDungRequest;
import vn.antam.clinic.dto.request.ThuocKeRequest;
import vn.antam.clinic.dto.request.YTaHoTroRequest;
import vn.antam.clinic.dto.response.ThietBiResponse;
import vn.antam.clinic.dto.response.YTaResponse;
import vn.antam.clinic.entity.DichVu;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.entity.ThietBi;
import vn.antam.clinic.entity.Thuoc;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.DichVuRepository;
import vn.antam.clinic.repository.LuotRepository;
import vn.antam.clinic.repository.ThietBiRepository;
import vn.antam.clinic.repository.ThuocRepository;
import vn.antam.clinic.repository.YTaRepository;

@ExtendWith(MockitoExtension.class)
class ChiTietLuotServiceTest {

    @Mock YTaRepository yTaRepo;
    @Mock DichVuRepository dichVuRepo;
    @Mock ThuocRepository thuocRepo;
    @Mock ThietBiRepository thietBiRepo;
    @Mock LuotRepository luotRepo;

    ChiTietLuotService service;

    @BeforeEach
    void setUp() {
        service = new ChiTietLuotService(yTaRepo, dichVuRepo, thuocRepo, thietBiRepo, luotRepo);
    }

    @Test
    void kiemTra_takesUnitPriceFromCatalogAndSumsTotal() {
        when(dichVuRepo.search(null)).thenReturn(List.of(new DichVu("DV02", "Nội soi", "Chẩn đoán hình ảnh", "Lần", new BigDecimal("250000"))));
        when(thuocRepo.search(null)).thenReturn(List.of(new Thuoc("T01", "Amoxicillin", null, "Viên", new BigDecimal("5000"), null)));
        when(thietBiRepo.findById("TB-CN1-01")).thenReturn(Optional.of(device(ThietBi.HOAT_DONG_TOT)));
        when(yTaRepo.findById("YT-CN1-01")).thenReturn(Optional.of(nurse(NhanVien.DANG_LAM_VIEC)));

        ChiTietLuotService.ChiTiet c = service.kiemTra(request(
                List.of(new YTaHoTroRequest("YT-CN1-01", " ")),
                List.of(new DichVuDungRequest("DV02", 2, null)),
                List.of(new ThuocKeRequest("T01", 10, "Ngày 2 viên")),
                List.of(new ThietBiDungRequest("TB-CN1-01", 1))), true);

        assertThat(c.dichVu().get(0).donGiaApDung()).isEqualByComparingTo("250000");
        assertThat(c.yTa().get(0).vaiTro()).isNull();
        assertThat(c.tongTien()).isEqualByComparingTo("650000");
        assertThat(c.withMaLuot("C-1").thuoc().get(0).maLuot()).isEqualTo("C-1");
    }

    @Test
    void kiemTra_examServiceInTreatmentVisit_throwsBR11() {
        when(dichVuRepo.search(null)).thenReturn(List.of(new DichVu("DV01", "Khám nội", DichVu.LOAI_KHAM_BENH, "Lần", new BigDecimal("150000"))));

        assertThatThrownBy(() -> service.kiemTra(request(null, List.of(new DichVuDungRequest("DV01", 1, null)), null, null), true))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("BR11"));
    }

    @Test
    void kiemTra_examServiceInExamVisit_isAllowed() {
        when(dichVuRepo.search(null)).thenReturn(List.of(new DichVu("DV01", "Khám nội", DichVu.LOAI_KHAM_BENH, "Lần", new BigDecimal("150000"))));

        ChiTietLuotService.ChiTiet c = service.kiemTra(request(null, List.of(new DichVuDungRequest("DV01", 1, null)), null, null), false);

        assertThat(c.tongTien()).isEqualByComparingTo("150000");
    }

    @Test
    void kiemTra_deviceOfOtherBranch_throwsBR6() {
        when(thietBiRepo.findById("TB-CN2-01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.kiemTra(request(null, null, null, List.of(new ThietBiDungRequest("TB-CN2-01", 1))), false))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("BR6"));
    }

    @Test
    void kiemTra_inactiveDevice_throwsDeviceInactive() {
        when(thietBiRepo.findById("TB-CN1-01")).thenReturn(Optional.of(device(ThietBi.NGUNG_HOAT_DONG)));

        assertThatThrownBy(() -> service.kiemTra(request(null, null, null, List.of(new ThietBiDungRequest("TB-CN1-01", 1))), false))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("DEVICE_INACTIVE"));
    }

    @Test
    void kiemTra_inactiveNurse_throwsStaffInactive() {
        when(yTaRepo.findById("YT-CN1-01")).thenReturn(Optional.of(nurse(NhanVien.NGUNG_HOAT_DONG)));

        assertThatThrownBy(() -> service.kiemTra(request(List.of(new YTaHoTroRequest("YT-CN1-01", null)), null, null, null), false))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("STAFF_INACTIVE"));
    }

    @Test
    void kiemTra_sameDrugTwice_throwsDuplicateRow() {
        List<ThuocKeRequest> thuoc = List.of(new ThuocKeRequest("T01", 1, null), new ThuocKeRequest("T01", 2, null));

        assertThatThrownBy(() -> service.kiemTra(request(null, null, thuoc, null), false))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_ROW"));
    }

    private static LuotChuaRequest request(List<YTaHoTroRequest> yTa, List<DichVuDungRequest> dv, List<ThuocKeRequest> thuoc,
                                           List<ThietBiDungRequest> tb) {
        return new LuotChuaRequest("CDDT-1", null, List.of("Tiêm"), null, "Đỡ", null, null, null, yTa, dv, thuoc, tb);
    }

    private static ThietBiResponse device(String tinhTrang) {
        return new ThietBiResponse("TB-CN1-01", "Máy nội soi", "Chẩn đoán", new BigDecimal("100000"), LocalDate.of(2022, 1, 1),
                tinhTrang, "CN1", 0);
    }

    private static YTaResponse nurse(String trangThai) {
        return new YTaResponse("YT-CN1-01", "Y tá Mai", null, "Nữ", null, null, new BigDecimal("2.34"), null, trangThai,
                "CN1-TMH", "Khoa TMH", null, null, List.of(), 0);
    }
}
