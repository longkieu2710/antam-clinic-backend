package vn.antam.clinic.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import vn.antam.clinic.dto.response.BenhNhanToanChuoiResponse;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.service.BenhNhanService;

@WebMvcTest(BenhNhanController.class)
@EnableConfigurationProperties(AppProperties.class)
class BenhNhanControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BenhNhanService service;

    @Test
    void searchCucBo_withQuery_returnsBranchMatches() throws Exception {
        when(service.searchCucBo("0912345678")).thenReturn(List.of(new BenhNhanToanChuoiResponse("BN-CN1-0001",
                "Nguyễn Văn C", null, "Nam", null, "0912345678", null, null, true, 0)));

        mvc.perform(get("/api/benh-nhan/cuc-bo").header("X-Site", "S1").param("q", "0912345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maBN").value("BN-CN1-0001"))
                .andExpect(jsonPath("$[0].coTaiChiNhanh").value(true));
    }

    @Test
    void create_invalidBloodTypeAndCccd_returns400() throws Exception {
        mvc.perform(post("/api/benh-nhan").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hoTen":"Nguyễn Văn C","nhomMau":"C","cccd":"123","ngaySinh":"2999-01-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nhomMau").exists())
                .andExpect(jsonPath("$.fieldErrors.cccd").value("CCCD gồm 12 chữ số"))
                .andExpect(jsonPath("$.fieldErrors.ngaySinh").value("Ngày sinh phải ở quá khứ"));
        verifyNoInteractions(service);
    }

    @Test
    void searchToanChuoi_passesQuery() throws Exception {
        when(service.searchToanChuoi("001095006666")).thenReturn(List.of(new BenhNhanToanChuoiResponse("BN02",
                "Trần Thị Bình", null, "Nữ", "001095006666", null, null, null, false, 0)));

        mvc.perform(get("/api/benh-nhan/toan-chuoi").param("q", "001095006666").header("X-Site", "S1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maBN").value("BN02"))
                .andExpect(jsonPath("$[0].coTaiChiNhanh").value(false));
    }

    @Test
    void delete_inUse_returns409() throws Exception {
        when(service.delete("BN01")).thenThrow(new BusinessException(HttpStatus.CONFLICT, "IN_USE", "đã có lần khám"));

        mvc.perform(delete("/api/benh-nhan/BN01").header("X-Site", "S1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"))
                .andExpect(jsonPath("$.site").value("S1"));
    }
}
