package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.dto.request.DichVuRequest;
import vn.antam.clinic.entity.DichVu;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.DichVuRepository;

@ExtendWith(MockitoExtension.class)
class DichVuServiceTest {

    @Mock
    DichVuRepository repo;

    DichVuService service;

    @BeforeEach
    void setUp() {
        service = new DichVuService(repo);
    }

    @Test
    void create_blankMaDV_generatesNextCode() {
        when(repo.findAllMaDV()).thenReturn(List.of("DV01", "DV04"));

        service.create(new DichVuRequest("", " Siêu âm ", "Chẩn đoán hình ảnh", " Lần ", new BigDecimal("200000")));

        verify(repo).insert(new DichVu("DV05", "Siêu âm", "Chẩn đoán hình ảnh", "Lần", new BigDecimal("200000")));
    }

    @Test
    void update_unknownService_throwsNotFound() {
        when(repo.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update("DV99", new DichVuRequest(null, "X", null, "Lần", BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_usedService_throwsInUse() {
        when(repo.countSuDungByDichVu()).thenReturn(Map.of("DV01", 7L));

        assertThatThrownBy(() -> service.delete("DV01"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("IN_USE");
        verify(repo, never()).delete(anyString());
    }

    @Test
    void delete_unusedService_deletes() {
        when(repo.countSuDungByDichVu()).thenReturn(Map.of());
        when(repo.delete("DV05")).thenReturn(1);

        assertThat(service.delete("DV05").mode()).isEqualTo("HARD");
    }
}
