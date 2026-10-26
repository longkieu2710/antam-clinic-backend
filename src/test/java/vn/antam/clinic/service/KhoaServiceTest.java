package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.KhoaRequest;
import vn.antam.clinic.dto.response.KhoaResponse;
import vn.antam.clinic.entity.Khoa;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.KhoaRepository;
import vn.antam.clinic.site.SiteContext;

@ExtendWith(MockitoExtension.class)
class KhoaServiceTest {

    @Mock
    KhoaRepository repo;

    KhoaService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("S0", null, Map.of(
                "s1", new AppProperties.Site("Cầu Giấy", "CN1", "jdbc:postgresql://localhost:5431/cn1", null, null)));
        service = new KhoaService(repo, props);
        SiteContext.set("S1");
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
    }

    @Test
    void create_blankMaKhoa_generatesCodeWithBranchPrefix() {
        when(repo.findAllMaKhoa()).thenReturn(List.of("CN1-TMH", "CN1-K01"));
        when(repo.findById("CN1-K02")).thenReturn(Optional.of(response("CN1-K02", 0, 0)));

        service.create(new KhoaRequest("", " Khoa Nhi ", " "));

        verify(repo).insert(new Khoa("CN1-K02", "Khoa Nhi", null, "CN1"));
    }

    @Test
    void create_givenMaKhoa_isUpperCasedAndKeepsCurrentBranch() {
        when(repo.findById("CN1-NHI")).thenReturn(Optional.of(response("CN1-NHI", 0, 0)));

        service.create(new KhoaRequest("cn1-nhi", "Khoa Nhi", "Nhi khoa"));

        verify(repo).insert(new Khoa("CN1-NHI", "Khoa Nhi", "Nhi khoa", "CN1"));
        verify(repo, never()).findAllMaKhoa();
    }

    @Test
    void update_unknownKhoa_throwsNotFound() {
        when(repo.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update("CN1-XXX", new KhoaRequest(null, "Khoa X", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_khoaWithStaffOrLuot_throwsInUse() {
        when(repo.findById("CN1-TMH")).thenReturn(Optional.of(response("CN1-TMH", 2, 5)));

        assertThatThrownBy(() -> service.delete("CN1-TMH"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("IN_USE");
        verify(repo, never()).delete(anyString());
    }

    @Test
    void delete_emptyKhoa_deletes() {
        when(repo.findById("CN1-K01")).thenReturn(Optional.of(response("CN1-K01", 0, 0)));

        assertThat(service.delete("CN1-K01").mode()).isEqualTo("HARD");
        verify(repo).delete("CN1-K01");
    }

    private static KhoaResponse response(String maKhoa, long soNhanVien, long soLuot) {
        return new KhoaResponse(maKhoa, "Khoa " + maKhoa, null, "CN1", soNhanVien, soLuot);
    }
}
