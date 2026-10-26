package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.YTaRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.YTaResponse;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.entity.YTa;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.NhanVienRepository;
import vn.antam.clinic.repository.YTaRepository;
import vn.antam.clinic.site.SiteContext;

@ExtendWith(MockitoExtension.class)
class YTaServiceTest {

    @Mock
    NhanVienRepository nhanVienRepo;

    @Mock
    YTaRepository yTaRepo;

    YTaService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("S0", null, Map.of(
                "s0", new AppProperties.Site("Trung tâm", null, "jdbc:postgresql://localhost:5430/hub", null, null),
                "s2", new AppProperties.Site("Đống Đa", "CN2", "jdbc:postgresql://localhost:5432/cn2", null, null)));
        service = new YTaService(nhanVienRepo, yTaRepo, props);
        SiteContext.set("S2");
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
    }

    @Test
    void create_blankMaNV_generatesNurseCodeOfCurrentBranch() {
        when(nhanVienRepo.findAllMaNV()).thenReturn(List.of("BS-CN2-05", "YT-CN2-01", "YT-CN2-02"));
        when(yTaRepo.findById("YT-CN2-03")).thenReturn(Optional.of(response("YT-CN2-03", 0)));

        YTaResponse created = service.create(request("", List.of("0901234567", "0901234567")));

        assertThat(created.maNV()).isEqualTo("YT-CN2-03");
        ArgumentCaptor<NhanVien> nv = ArgumentCaptor.forClass(NhanVien.class);
        verify(nhanVienRepo).insert(nv.capture());
        assertThat(nv.getValue().loaiNV()).isEqualTo(NhanVien.LOAI_Y_TA);
        assertThat(nv.getValue().trangThai()).isEqualTo("Đang làm việc");
        assertThat(nv.getValue().ngayVaoLam()).isEqualTo(LocalDate.now());
        verify(yTaRepo).insert(new YTa("YT-CN2-03", "Cử nhân Điều dưỡng", null));
        verify(nhanVienRepo).replacePhones("YT-CN2-03", List.of("0901234567"));
    }

    @Test
    void update_unknownMaNV_throwsNotFoundAndSkipsChildTables() {
        when(nhanVienRepo.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update("YT-CN2-77", request(null, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(yTaRepo, never()).update(any());
        verify(nhanVienRepo, never()).replacePhones(anyString(), any());
    }

    @Test
    void delete_nurseWithHoTro_marksInactiveInsteadOfDeleting() {
        when(yTaRepo.findById("YT-CN2-01")).thenReturn(Optional.of(response("YT-CN2-01", 4)));
        when(yTaRepo.countHoTro("YT-CN2-01")).thenReturn(4L);

        DeleteResponse result = service.delete("YT-CN2-01");

        assertThat(result.mode()).isEqualTo("SOFT");
        verify(nhanVienRepo).markInactive("YT-CN2-01");
        verify(yTaRepo, never()).delete(anyString());
        verify(nhanVienRepo, never()).delete(anyString());
    }

    @Test
    void delete_nurseWithoutHoTro_deletesChildBeforeParent() {
        when(yTaRepo.findById("YT-CN2-02")).thenReturn(Optional.of(response("YT-CN2-02", 0)));
        when(yTaRepo.countHoTro("YT-CN2-02")).thenReturn(0L);

        DeleteResponse result = service.delete("YT-CN2-02");

        assertThat(result.mode()).isEqualTo("HARD");
        InOrder order = inOrder(yTaRepo, nhanVienRepo);
        order.verify(yTaRepo).delete("YT-CN2-02");
        order.verify(nhanVienRepo).delete("YT-CN2-02");
    }

    private static YTaRequest request(String maNV, List<String> phones) {
        return new YTaRequest(maNV, " Lê Thị B ", LocalDate.of(1995, 1, 2), "Nữ", null, "Hà Nội",
                new BigDecimal("2.34"), null, null, "CN2-RHM", "Cử nhân Điều dưỡng", " ", phones);
    }

    private static YTaResponse response(String maNV, long soLanHoTro) {
        return new YTaResponse(maNV, "Lê Thị B", LocalDate.of(1995, 1, 2), "Nữ", null, "Hà Nội", new BigDecimal("2.34"),
                LocalDate.now(), "Đang làm việc", "CN2-RHM", "Khoa RHM", "Cử nhân Điều dưỡng", null, List.of(), soLanHoTro);
    }
}
