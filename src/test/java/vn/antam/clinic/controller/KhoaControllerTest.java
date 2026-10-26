package vn.antam.clinic.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.response.KhoaResponse;
import vn.antam.clinic.service.KhoaService;

@WebMvcTest(KhoaController.class)
@EnableConfigurationProperties(AppProperties.class)
class KhoaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KhoaService service;

    @Test
    void create_badCodeAndBlankName_returns400() throws Exception {
        mvc.perform(post("/api/khoa").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maKhoa":"CN1 TMH","tenKhoa":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maKhoa").value("Mã khoa chỉ gồm chữ, số và dấu gạch ngang"))
                .andExpect(jsonPath("$.fieldErrors.tenKhoa").value("Nhập tên khoa"));
        verifyNoInteractions(service);
    }

    @Test
    void update_validBody_returnsUpdated() throws Exception {
        when(service.update(eq("CN1-TMH"), any())).thenReturn(new KhoaResponse("CN1-TMH", "Khoa TMH", null, "CN1", 2, 5));

        mvc.perform(put("/api/khoa/CN1-TMH").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenKhoa":"Khoa TMH"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soLuot").value(5));
    }
}
