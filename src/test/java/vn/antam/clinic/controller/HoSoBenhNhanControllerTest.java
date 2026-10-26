package vn.antam.clinic.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.service.HoSoBenhNhanService;

@WebMvcTest(HoSoBenhNhanController.class)
@EnableConfigurationProperties(AppProperties.class)
class HoSoBenhNhanControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    HoSoBenhNhanService service;

    @Test
    void hoSo_unknownPatient_returns404() throws Exception {
        when(service.hoSo("XX")).thenThrow(BusinessException.notFound("bệnh nhân", "XX"));

        mvc.perform(get("/api/benh-nhan/XX/ho-so").header("X-Site", "S1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void hoSo_missingSite_returns400() throws Exception {
        mvc.perform(get("/api/benh-nhan/BN01/ho-so"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
