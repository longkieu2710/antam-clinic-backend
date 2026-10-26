package vn.antam.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.dto.request.BenhNhanRequest;
import vn.antam.clinic.dto.response.BenhNhanResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.entity.BenhNhan;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BenhNhanRepository;

@ExtendWith(MockitoExtension.class)
class BenhNhanServiceTest {

    @Mock
    BenhNhanRepository repo;

    BenhNhanService service;

    @BeforeEach
    void setUp() {
        service = new BenhNhanService(repo);
    }

    @Test
    void create_blankMaBN_generatesCodeUniqueAcrossChain() {
        when(repo.findAllMaBNToanChuoi()).thenReturn(List.of("BN01", "BN02", "BN07"));
        when(repo.findById("BN08")).thenReturn(Optional.of(response("BN08", 0)));

        BenhNhanResponse created = service.create(request(" "));

        assertThat(created.maBN()).isEqualTo("BN08");
        ArgumentCaptor<BenhNhan> bn = ArgumentCaptor.forClass(BenhNhan.class);
        verify(repo).insert(bn.capture());
        assertThat(bn.getValue().hoTen()).isEqualTo("Nguyễn Văn C");
        assertThat(bn.getValue().cccd()).as("chuỗi rỗng → NULL để không trùng UNIQUE").isNull();
    }

    @Test
    void create_chainUnreachable_throws503WithoutInserting() {
        when(repo.findAllMaBNToanChuoi()).thenThrow(new DataAccessResourceFailureException("fdw down"));

        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(((BusinessException) e).getCode()).isEqualTo("CHAIN_UNAVAILABLE");
                });
        verify(repo, never()).insert(any());
    }

    @Test
    void create_givenExistingChainCode_keepsItWithoutReadingChain() {
        when(repo.findById("BN02")).thenReturn(Optional.of(response("BN02", 0)));

        service.create(request("BN02"));

        verify(repo, never()).findAllMaBNToanChuoi();
    }

    @Test
    void delete_patientWithLanKham_throwsInUse() {
        when(repo.findById("BN01")).thenReturn(Optional.of(response("BN01", 2)));

        assertThatThrownBy(() -> service.delete("BN01"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("IN_USE");
        verify(repo, never()).delete(anyString());
    }

    @Test
    void delete_patientWithoutLanKham_deletesHard() {
        when(repo.findById("BN09")).thenReturn(Optional.of(response("BN09", 0)));
        when(repo.delete("BN09")).thenReturn(1);

        DeleteResponse result = service.delete("BN09");

        assertThat(result.mode()).isEqualTo("HARD");
    }

    @Test
    void searchToanChuoi_blankQuery_returnsEmptyWithoutQuerying() {
        assertThat(service.searchToanChuoi("  ")).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test
    void searchToanChuoi_chainUnreachable_throws503() {
        when(repo.searchToanChuoi("001090003333")).thenThrow(new DataAccessResourceFailureException("fdw down"));

        assertThatThrownBy(() -> service.searchToanChuoi("001090003333"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BenhNhanRequest request(String maBN) {
        return new BenhNhanRequest(maBN, " Nguyễn Văn C ", null, "Nam", "", "0912345678", null, null, "O", null);
    }

    private static BenhNhanResponse response(String maBN, long soLanKham) {
        return new BenhNhanResponse(maBN, "Nguyễn Văn C", null, "Nam", null, "0912345678", null, null, "O", null,
                soLanKham, null, 0);
    }
}
