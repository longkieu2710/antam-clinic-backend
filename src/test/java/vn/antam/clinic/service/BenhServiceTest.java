package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.dto.request.BenhRequest;
import vn.antam.clinic.dto.response.BenhResponse;
import vn.antam.clinic.entity.Benh;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BenhRepository;

@ExtendWith(MockitoExtension.class)
class BenhServiceTest {

    @Mock
    BenhRepository repo;

    BenhService service;

    @BeforeEach
    void setUp() {
        service = new BenhService(repo);
    }

    @Test
    void create_blankMaBenh_generatesNextCodeAndUpperCasesIcd() {
        when(repo.findAllMaBenh()).thenReturn(List.of("B01", "B04"));

        BenhResponse created = service.create(new BenhRequest(null, " Viêm tai giữa ", " h66 ", ""));

        assertThat(created.maBenh()).isEqualTo("B05");
        verify(repo).insert(new Benh("B05", "Viêm tai giữa", "H66", null));
    }

    @Test
    void listMaster_chainUnreachable_returnsRowsWithoutUsage() {
        when(repo.search(null)).thenReturn(List.of(new Benh("B01", "Viêm họng", "J02", null)));
        when(repo.countChanDoanByBenh()).thenThrow(new DataAccessResourceFailureException("fdw down"));

        List<BenhResponse> rows = service.listMaster(null);

        assertThat(rows).singleElement().satisfies(b -> assertThat(b.soLanChanDoan()).isNull());
    }

    @Test
    void delete_diseaseDiagnosedSomewhere_throwsInUse() {
        when(repo.countChanDoanByBenh()).thenReturn(Map.of("B01", 3L));

        assertThatThrownBy(() -> service.delete("B01"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(repo, never()).delete(anyString());
    }

    @Test
    void delete_usageUnreadable_refusesWith503() {
        when(repo.countChanDoanByBenh()).thenThrow(new DataAccessResourceFailureException("fdw down"));

        assertThatThrownBy(() -> service.delete("B09"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("CHECK_UNAVAILABLE");
        verify(repo, never()).delete(anyString());
    }

    @Test
    void delete_unusedDisease_deletes() {
        when(repo.countChanDoanByBenh()).thenReturn(Map.of("B01", 3L));
        when(repo.delete("B09")).thenReturn(1);

        assertThat(service.delete("B09").mode()).isEqualTo("HARD");
    }
}
