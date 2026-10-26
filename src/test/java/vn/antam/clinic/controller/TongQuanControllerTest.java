package vn.antam.clinic.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import vn.antam.clinic.service.TongQuanService;

@WebMvcTest(TongQuanController.class)
@EnableConfigurationProperties(AppProperties.class)
class TongQuanControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TongQuanService service;

    @Test
    void tongQuan_missingSite_returns400() throws Exception {
        mvc.perform(get("/api/tong-quan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SITE"));
        verifyNoInteractions(service);
    }

    @Test
    void unknownApi_returns404WithRebuildHint() throws Exception {
        mvc.perform(get("/api/khong-co-api").header("X-Site", "S1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("bản build cũ")));
    }

    @Test
    void tongQuan_withSite_callsService() throws Exception {
        mvc.perform(get("/api/tong-quan").header("X-Site", "S1")).andExpect(status().isOk());

        verify(service).tongQuan();
    }
}
