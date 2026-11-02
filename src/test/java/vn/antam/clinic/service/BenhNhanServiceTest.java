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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.antam.clinic.dto.request.BenhNhanRequest;
import vn.antam.clinic.dto.response.BenhNhanResponse;
import vn.antam.clinic.dto.response.BenhNhanTiepDonResponse;
import vn.antam.clinic.dto.response.ChiMucBenhNhanResponse;
import vn.antam.clinic.dto.response.MaToanChuoiResponse;
import vn.antam.clinic.dto.response.TiepDonResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.entity.BenhNhan;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.repository.BenhNhanRepository;
import vn.antam.clinic.repository.ToanChuoiRepository;
import vn.antam.clinic.site.SiteContext;

@ExtendWith(MockitoExtension.class)
class BenhNhanServiceTest {

    @Mock
    BenhNhanRepository repo;

    @Mock
    ToanChuoiRepository toanChuoiRepo;

    BenhNhanService service;

    @BeforeEach
    void setUp() {
        SiteContext.set("S1");
        service = new BenhNhanService(repo, toanChuoiRepo, LuotFixtures.props());
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
    }

    @Test
    void create_blankMaBN_generatesBranchPrefixedCodeAcrossChain() {
        when(repo.findAllMaBN()).thenReturn(List.of("BN01", "BN-CN1-0001"));
        when(toanChuoiRepo.maBenhNhanTheoTienTo("BN-CN1-")).thenReturn(List.of(
                new MaToanChuoiResponse("BN-CN1-0002", true), new MaToanChuoiResponse(null, false)));
        when(repo.findById("BN-CN1-0003")).thenReturn(Optional.of(response("BN-CN1-0003", 0)));

        BenhNhanResponse created = service.create(request(" "));

        assertThat(created.maBN()).isEqualTo("BN-CN1-0003");
        ArgumentCaptor<BenhNhan> bn = ArgumentCaptor.forClass(BenhNhan.class);
        verify(repo).insert(bn.capture());
        assertThat(bn.getValue().hoTen()).isEqualTo("Nguyễn Văn C");
        assertThat(bn.getValue().cccd()).as("chuỗi rỗng → NULL để không trùng UNIQUE").isNull();
    }

    @Test
    void create_hubUnreachable_generatesCodeFromLocalTable() {
        when(repo.findAllMaBN()).thenReturn(List.of("BN02", "BN-CN1-0004"));
        when(toanChuoiRepo.maBenhNhanTheoTienTo("BN-CN1-")).thenThrow(new DataAccessResourceFailureException("S0 tắt"));
        when(repo.findById("BN-CN1-0005")).thenReturn(Optional.of(response("BN-CN1-0005", 0)));

        BenhNhanResponse created = service.create(request(null));

        assertThat(created.maBN()).isEqualTo("BN-CN1-0005");
        verify(repo).insert(any());
    }

    @Test
    void create_givenExistingChainCode_keepsItWithoutReadingChain() {
        when(repo.findById("BN02")).thenReturn(Optional.of(response("BN02", 0)));

        service.create(request("BN02"));

        verify(repo, never()).findAllMaBN();
        verifyNoInteractions(toanChuoiRepo);
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
    void tiepDon_blankQuery_returnsEmptyWithoutQuerying() {
        assertThat(service.tiepDon("  ").taiChiNhanh()).isEmpty();
        verifyNoInteractions(repo, toanChuoiRepo);
    }

    @Test
    void tiepDon_nameQuery_searchesBranchOnly() {
        when(repo.searchTiepDon("Nguyễn")).thenReturn(List.of(tiepDon("BN01", "001090003333")));

        TiepDonResponse result = service.tiepDon("Nguyễn");

        assertThat(result.taiChiNhanh()).hasSize(1);
        assertThat(result.daTraToanChuoi()).isFalse();
        verifyNoInteractions(toanChuoiRepo);
    }

    @Test
    void tiepDon_cccdAlreadyAtBranch_doesNotAskHub() {
        when(repo.searchTiepDon("001090003333")).thenReturn(List.of(tiepDon("BN01", "001090003333")));

        assertThat(service.tiepDon("001090003333").noiKhac()).isEmpty();
        verifyNoInteractions(toanChuoiRepo);
    }

    @Test
    void tiepDon_cccdOfOtherBranch_returnsOnlyCodeAndBranch() {
        when(repo.searchTiepDon("001095006666")).thenReturn(List.of());
        when(toanChuoiRepo.timMaBenhNhan("001095006666")).thenReturn(List.of(
                new ChiMucBenhNhanResponse("BN02", "CN2", true), new ChiMucBenhNhanResponse(null, "CN3", false)));

        TiepDonResponse result = service.tiepDon("001095006666");

        assertThat(result.noiKhac()).containsExactly(new ChiMucBenhNhanResponse("BN02", "CN2", true));
        assertThat(result.daTraToanChuoi()).isTrue();
        assertThat(result.canhBao()).contains("CN3");
    }

    @Test
    void tiepDon_hubUnreachable_returnsBranchResultsWithWarning() {
        when(repo.searchTiepDon("001095006666")).thenReturn(List.of());
        when(toanChuoiRepo.timMaBenhNhan("001095006666")).thenThrow(new DataAccessResourceFailureException("S0 tắt"));

        TiepDonResponse result = service.tiepDon("001095006666");

        assertThat(result.noiKhac()).isEmpty();
        assertThat(result.daTraToanChuoi()).isFalse();
        assertThat(result.canhBao()).contains("Trung tâm");
    }

    private static BenhNhanRequest request(String maBN) {
        return new BenhNhanRequest(maBN, " Nguyễn Văn C ", null, "Nam", "", "0912345678", null, null, "O", null);
    }

    private static BenhNhanTiepDonResponse tiepDon(String maBN, String cccd) {
        return new BenhNhanTiepDonResponse(maBN, "Nguyễn Văn An", null, "Nam", cccd, null, null, null, 0);
    }

    private static BenhNhanResponse response(String maBN, long soLanKham) {
        return new BenhNhanResponse(maBN, "Nguyễn Văn C", null, "Nam", null, "0912345678", null, null, "O", null,
                soLanKham, null, 0);
    }
}
