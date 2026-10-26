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
import vn.antam.clinic.dto.request.BacSiRequest;
import vn.antam.clinic.dto.response.BacSiResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.entity.BacSi;
import vn.antam.clinic.entity.NhanVien;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BacSiRepository;
import vn.antam.clinic.repository.NhanVienRepository;
import vn.antam.clinic.site.SiteContext;

@ExtendWith(MockitoExtension.class)
class BacSiServiceTest {

    @Mock
    NhanVienRepository nhanVienRepo;

    @Mock
    BacSiRepository bacSiRepo;

    BacSiService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("S0", null, Map.of(
                "s0", new AppProperties.Site("Trung tâm", null, "jdbc:postgresql://localhost:5430/hub", null, null),
                "s1", new AppProperties.Site("Cầu Giấy", "CN1", "jdbc:postgresql://localhost:5431/cn1", null, null)));
        service = new BacSiService(nhanVienRepo, bacSiRepo, props);
        SiteContext.set("S1");
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
    }

    @Test
    void create_blankMaNV_generatesNextCodeOfCurrentBranch() {
        when(nhanVienRepo.findAllMaNV()).thenReturn(List.of("BS-CN1-01", "BS-CN1-03", "YT-CN1-09"));
        when(bacSiRepo.findById("BS-CN1-04")).thenReturn(Optional.of(response("BS-CN1-04", 0)));

        BacSiResponse created = service.create(request(" ", List.of("0901234567", " ", "0901234567")));

        assertThat(created.maNV()).isEqualTo("BS-CN1-04");
        ArgumentCaptor<NhanVien> nv = ArgumentCaptor.forClass(NhanVien.class);
        verify(nhanVienRepo).insert(nv.capture());
        assertThat(nv.getValue().maNV()).isEqualTo("BS-CN1-04");
        assertThat(nv.getValue().hoTen()).isEqualTo("Nguyễn Văn A");
        assertThat(nv.getValue().loaiNV()).isEqualTo(NhanVien.LOAI_BAC_SI);
        assertThat(nv.getValue().trangThai()).isEqualTo("Đang làm việc");
        assertThat(nv.getValue().ngayVaoLam()).isEqualTo(LocalDate.now());
        assertThat(nv.getValue().cccd()).as("chuỗi rỗng phải thành NULL để không trùng UNIQUE").isNull();
        verify(bacSiRepo).insert(new BacSi("BS-CN1-04", "Nội tổng quát", "ThS", "CCHN-001"));
        verify(nhanVienRepo).replacePhones("BS-CN1-04", List.of("0901234567"));
    }

    @Test
    void create_givenMaNV_keepsItWithoutGenerating() {
        when(bacSiRepo.findById("BS-CN1-99")).thenReturn(Optional.of(response("BS-CN1-99", 0)));

        service.create(request("BS-CN1-99", List.of()));

        verify(nhanVienRepo, never()).findAllMaNV();
        verify(bacSiRepo).insert(new BacSi("BS-CN1-99", "Nội tổng quát", "ThS", "CCHN-001"));
    }

    @Test
    void update_unknownMaNV_throwsNotFoundAndSkipsChildTables() {
        when(nhanVienRepo.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update("BS-CN1-77", request(null, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(bacSiRepo, never()).update(any());
        verify(nhanVienRepo, never()).replacePhones(anyString(), any());
    }

    @Test
    void get_unknownMaNV_throwsNotFound() {
        when(bacSiRepo.findById("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("X"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("'X'");
    }

    @Test
    void delete_doctorWithLuot_marksInactiveInsteadOfDeleting() {
        when(bacSiRepo.findById("BS-CN1-01")).thenReturn(Optional.of(response("BS-CN1-01", 3)));
        when(bacSiRepo.countLuot("BS-CN1-01")).thenReturn(3L);

        DeleteResponse result = service.delete("BS-CN1-01");

        assertThat(result.mode()).isEqualTo("SOFT");
        verify(nhanVienRepo).markInactive("BS-CN1-01");
        verify(bacSiRepo, never()).delete(anyString());
        verify(nhanVienRepo, never()).delete(anyString());
    }

    @Test
    void delete_doctorWithoutLuot_deletesChildBeforeParent() {
        when(bacSiRepo.findById("BS-CN1-02")).thenReturn(Optional.of(response("BS-CN1-02", 0)));
        when(bacSiRepo.countLuot("BS-CN1-02")).thenReturn(0L);

        DeleteResponse result = service.delete("BS-CN1-02");

        assertThat(result.mode()).isEqualTo("HARD");
        InOrder order = inOrder(bacSiRepo, nhanVienRepo);
        order.verify(bacSiRepo).delete("BS-CN1-02");
        order.verify(nhanVienRepo).delete("BS-CN1-02");
    }

    private static BacSiRequest request(String maNV, List<String> phones) {
        return new BacSiRequest(maNV, " Nguyễn Văn A ", LocalDate.of(1985, 5, 20), "Nam", "", "Hà Nội",
                new BigDecimal("2.34"), null, null, "K01", "Nội tổng quát", "ThS", "CCHN-001", phones);
    }

    private static BacSiResponse response(String maNV, long soLuot) {
        return new BacSiResponse(maNV, "Nguyễn Văn A", LocalDate.of(1985, 5, 20), "Nam", null, "Hà Nội",
                new BigDecimal("2.34"), LocalDate.now(), "Đang làm việc", "K01", "Khoa Nội", "Nội tổng quát", "ThS",
                "CCHN-001", List.of(), soLuot);
    }
}
