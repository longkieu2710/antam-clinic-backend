package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.request.ThietBiRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.ThietBiResponse;
import vn.antam.clinic.entity.ThietBi;
import vn.antam.clinic.repository.ThietBiRepository;
import vn.antam.clinic.site.SiteContext;

@ExtendWith(MockitoExtension.class)
class ThietBiServiceTest {

    @Mock
    ThietBiRepository repo;

    ThietBiService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("S0", null, Map.of(
                "s3", new AppProperties.Site("Ba Đình", "CN3", "jdbc:postgresql://localhost:5433/cn3", null, null)));
        service = new ThietBiService(repo, props);
        SiteContext.set("S3");
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
    }

    @Test
    void create_blankMaTB_generatesCodeAndDefaultsStatus() {
        when(repo.findAllMaTB()).thenReturn(List.of("TB-CN3-01"));
        when(repo.findById("TB-CN3-02")).thenReturn(Optional.of(response("TB-CN3-02", 0)));

        service.create(new ThietBiRequest(null, " Máy siêu âm ", "", new BigDecimal("50000"), null, " "));

        verify(repo).insert(new ThietBi("TB-CN3-02", "Máy siêu âm", null, new BigDecimal("50000"), null,
                ThietBi.HOAT_DONG_TOT, "CN3"));
    }

    @Test
    void delete_usedDevice_marksStoppedInsteadOfDeleting() {
        when(repo.findById("TB-CN3-01")).thenReturn(Optional.of(response("TB-CN3-01", 3)));

        DeleteResponse result = service.delete("TB-CN3-01");

        assertThat(result.mode()).isEqualTo("SOFT");
        verify(repo).updateTinhTrang("TB-CN3-01", ThietBi.NGUNG_HOAT_DONG);
        verify(repo, never()).delete(anyString());
    }

    @Test
    void delete_unusedDevice_deletesHard() {
        when(repo.findById("TB-CN3-05")).thenReturn(Optional.of(response("TB-CN3-05", 0)));

        assertThat(service.delete("TB-CN3-05").mode()).isEqualTo("HARD");
        verify(repo).delete("TB-CN3-05");
    }

    private static ThietBiResponse response(String maTB, long soLanSuDung) {
        return new ThietBiResponse(maTB, "Máy siêu âm", null, new BigDecimal("50000"), null, ThietBi.HOAT_DONG_TOT,
                "CN3", soLanSuDung);
    }
}
