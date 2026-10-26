package vn.antam.clinic.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.service.LuotChuaService;

@WebMvcTest(DotDieuTriController.class)
@EnableConfigurationProperties(AppProperties.class)
class DotDieuTriControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LuotChuaService service;

    @Test
    void ngung_withoutBody_passesNullRequest() throws Exception {
        mvc.perform(put("/api/dot-dieu-tri/CDDT-CN1-001/ngung").header("X-Site", "S1"))
                .andExpect(status().isOk());

        verify(service).ngungDieuTri(eq("CDDT-CN1-001"), isNull());
    }

    @Test
    void dangDieuTri_routesToBranchOfHeader() throws Exception {
        mvc.perform(get("/api/benh-nhan/BN01/dot-dang-dieu-tri").header("X-Site", "S3"))
                .andExpect(status().isOk());

        verify(service).dotDangDieuTri("BN01");
    }
}
