package vn.antam.clinic.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.response.BacSiResponse;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.service.BacSiService;

/** Tầng web: validation, SiteFilter (X-Site) và định dạng lỗi của GlobalExceptionHandler. */
@WebMvcTest(BacSiController.class)
@EnableConfigurationProperties(AppProperties.class)
class BacSiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BacSiService service;

    @Test
    void create_validBody_returns201() throws Exception {
        when(service.create(any())).thenReturn(response("BS-CN1-04"));

        mvc.perform(post("/api/bac-si").header("X-Site", "S1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hoTen":"Nguyễn Văn A","heSoLuong":2.34,"maKhoa":"K01","soDienThoai":["0901234567"]}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Site-Used", "S1"))
                .andExpect(jsonPath("$.maNV").value("BS-CN1-04"));
    }

    @Test
    void create_invalidBody_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/bac-si").header("X-Site", "S1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hoTen":"","heSoLuong":0,"maKhoa":"K01","cccd":"123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors.hoTen").value("Nhập họ tên"))
                .andExpect(jsonPath("$.fieldErrors.heSoLuong").value("Hệ số lương phải > 0"))
                .andExpect(jsonPath("$.fieldErrors.cccd").value("CCCD gồm 12 chữ số"));
        verifyNoInteractions(service);
    }

    @Test
    void search_missingSiteHeader_returns400MissingSite() throws Exception {
        mvc.perform(get("/api/bac-si"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SITE"));
        verifyNoInteractions(service);
    }

    @Test
    void search_hubSiteHeader_returns400MissingSite() throws Exception {
        // S0 không phải chi nhánh: API chi nhánh không được chạy ở trạm trung tâm
        mvc.perform(get("/api/bac-si").header("X-Site", "S0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SITE"));
        verifyNoInteractions(service);
    }

    @Test
    void get_unknownMaNV_returns404WithSite() throws Exception {
        when(service.get("X")).thenThrow(BusinessException.notFound("bác sĩ", "X"));

        mvc.perform(get("/api/bac-si/X").header("X-Site", "s2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.site").value("S2"));
    }

    private static BacSiResponse response(String maNV) {
        return new BacSiResponse(maNV, "Nguyễn Văn A", null, null, null, null, new BigDecimal("2.34"),
                LocalDate.now(), "Đang làm việc", "K01", "Khoa Nội", null, null, null, List.of("0901234567"), 0);
    }
}
