package vn.antam.clinic.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import vn.antam.clinic.service.ThietBiService;

@WebMvcTest(ThietBiController.class)
@EnableConfigurationProperties(AppProperties.class)
class ThietBiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ThietBiService service;

    @Test
    void create_negativePriceAndFuturePurchase_returns400() throws Exception {
        mvc.perform(post("/api/thiet-bi").header("X-Site", "S1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenTB":"Máy","donGiaSuDung":-1,"ngayMua":"2999-01-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.donGiaSuDung").value("Đơn giá sử dụng phải ≥ 0"))
                .andExpect(jsonPath("$.fieldErrors.ngayMua").value("Ngày mua không được ở tương lai"));
        verifyNoInteractions(service);
    }

    @Test
    void search_hubSite_returns400MissingSite() throws Exception {
        mvc.perform(get("/api/thiet-bi").header("X-Site", "S0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SITE"));
        verifyNoInteractions(service);
    }
}
