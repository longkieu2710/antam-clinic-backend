package vn.antam.clinic.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.YTaResponse;
import vn.antam.clinic.service.YTaService;

@WebMvcTest(YTaController.class)
@EnableConfigurationProperties(AppProperties.class)
class YTaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    YTaService service;

    @Test
    void create_validBody_returns201() throws Exception {
        when(service.create(any())).thenReturn(new YTaResponse("YT-CN1-02", "Lê Thị B", null, "Nữ", null, null,
                new BigDecimal("2.34"), LocalDate.now(), "Đang làm việc", "CN1-TMH", "Khoa TMH", null, null, List.of(), 0));

        mvc.perform(post("/api/y-ta").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hoTen":"Lê Thị B","heSoLuong":2.34,"maKhoa":"CN1-TMH"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Site-Used", "S1"))
                .andExpect(jsonPath("$.maNV").value("YT-CN1-02"));
    }

    @Test
    void create_invalidBody_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/y-ta").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hoTen":" ","heSoLuong":-1,"maKhoa":"","gioiTinh":"X"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors.hoTen").value("Nhập họ tên"))
                .andExpect(jsonPath("$.fieldErrors.maKhoa").value("Chọn khoa"))
                .andExpect(jsonPath("$.fieldErrors.gioiTinh").exists());
        verifyNoInteractions(service);
    }

    @Test
    void delete_missingSiteHeader_returns400MissingSite() throws Exception {
        mvc.perform(delete("/api/y-ta/YT-CN1-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SITE"));
        verifyNoInteractions(service);
    }

    @Test
    void delete_soft_returnsMode() throws Exception {
        when(service.delete("YT-CN1-01")).thenReturn(DeleteResponse.soft("đã chuyển Ngừng hoạt động"));

        mvc.perform(delete("/api/y-ta/YT-CN1-01").header("X-Site", "S1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SOFT"));
    }
}
