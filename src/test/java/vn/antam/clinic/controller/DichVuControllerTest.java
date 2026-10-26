package vn.antam.clinic.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.service.DichVuService;

@WebMvcTest(DichVuController.class)
@EnableConfigurationProperties(AppProperties.class)
class DichVuControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DichVuService service;

    @Test
    void create_missingFields_returns400() throws Exception {
        mvc.perform(post("/api/master/dich-vu").contentType(MediaType.APPLICATION_JSON).content("""
                        {"tenDV":"","donViTinh":"","donGia":-5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.tenDV").value("Nhập tên dịch vụ"))
                .andExpect(jsonPath("$.fieldErrors.donViTinh").value("Nhập đơn vị tính"))
                .andExpect(jsonPath("$.fieldErrors.donGia").value("Đơn giá phải ≥ 0"));
        verifyNoInteractions(service);
    }

    @Test
    void delete_inUse_returns409() throws Exception {
        when(service.delete("DV01")).thenThrow(new BusinessException(HttpStatus.CONFLICT, "IN_USE", "đã dùng"));

        mvc.perform(delete("/api/master/dich-vu/DV01"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"))
                .andExpect(jsonPath("$.site").value("S0"));
    }
}
