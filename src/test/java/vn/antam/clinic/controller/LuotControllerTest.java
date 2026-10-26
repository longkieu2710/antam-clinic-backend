package vn.antam.clinic.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.response.TaoLuotResponse;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.service.LuotChuaService;
import vn.antam.clinic.service.LuotKhamService;
import vn.antam.clinic.service.LuotService;

@WebMvcTest(LuotController.class)
@EnableConfigurationProperties(AppProperties.class)
class LuotControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean LuotService luotService;
    @MockitoBean LuotKhamService luotKhamService;
    @MockitoBean LuotChuaService luotChuaService;

    @Test
    void createKham_validBody_returns201WithCode() throws Exception {
        when(luotKhamService.create(any())).thenReturn(new TaoLuotResponse("K-CN1-TMH-BS-CN1-01-261023-001", "KHAM", "BN01",
                "Nguyễn Văn An", List.of("CDDT-CN1-010"), null, false, false, new BigDecimal("150000"), List.of()));

        mvc.perform(post("/api/luot-kham").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maBN":"BN01","maKhoa":"CN1-TMH","maBS":"BS-CN1-01","thoiGian":"2026-10-23T09:30:00",
                                 "chanDoan":[{"maBenh":"B01","mucDo":"Nhẹ","soLanChuaCanThiet":2}],
                                 "yTa":[{"maYT":"YT-CN1-01"}],"dichVu":[{"maDV":"DV01","soLuong":1}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maLuot").value("K-CN1-TMH-BS-CN1-01-261023-001"))
                .andExpect(jsonPath("$.maCDDT[0]").value("CDDT-CN1-010"));
    }

    @Test
    void createKham_invalidNestedRows_returns400WithIndexedFieldErrors() throws Exception {
        mvc.perform(post("/api/luot-kham").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maBN":"BN01","maKhoa":"","maBS":"BS-CN1-01",
                                 "chanDoan":[{"maBenh":"B01","mucDo":"Rất nặng","soLanChuaCanThiet":0}],
                                 "thuoc":[{"maThuoc":"T01","soLuong":0}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maKhoa").value("Chọn khoa"))
                .andExpect(jsonPath("$.fieldErrors['chanDoan[0].mucDo']").exists())
                .andExpect(jsonPath("$.fieldErrors['chanDoan[0].soLanChuaCanThiet']").exists())
                .andExpect(jsonPath("$.fieldErrors['thuoc[0].soLuong']").value("Số lượng phải > 0 (BR8)"));
        verifyNoInteractions(luotKhamService);
    }

    @Test
    void createChua_withoutHinhThuc_returns400() throws Exception {
        mvc.perform(post("/api/luot-chua").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maCDDT":"CDDT-CN1-001","hinhThuc":[],"tinhTrangSauChua":"Đỡ"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hinhThuc").value("Chọn ít nhất một hình thức chữa"));
        verifyNoInteractions(luotChuaService);
    }

    @Test
    void createChua_ruleViolation_returns422WithCode() throws Exception {
        when(luotChuaService.create(any())).thenThrow(new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "BR11", "không tính tiền khám"));

        mvc.perform(post("/api/luot-chua").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maCDDT":"CDDT-CN1-001","hinhThuc":["Tiêm"],"tinhTrangSauChua":"Đỡ","dichVu":[{"maDV":"DV01","soLuong":1}]}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BR11"));
    }

    @Test
    void search_bindsIsoDatesAndFilters() throws Exception {
        when(luotService.search(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/luot").header("X-Site", "S2").param("tu", "2026-10-01").param("den", "2026-10-31").param("loai", "CHUA"))
                .andExpect(status().isOk());

        verify(luotService).search(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)), eq("CHUA"), isNull(), isNull(), isNull());
    }

    @Test
    void delete_missingSite_returns400() throws Exception {
        mvc.perform(delete("/api/luot/K-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SITE"));
        verifyNoInteractions(luotService);
    }
}
