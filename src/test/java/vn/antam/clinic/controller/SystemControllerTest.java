package vn.antam.clinic.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.service.AppModeService;
import vn.antam.clinic.service.SiteHealthService;

/** Bản chi nhánh CN2 (APP_MODE=branch, APP_LOCAL_SITE=S2) dựng trên application.yml thật. */
@WebMvcTest(value = SystemController.class, properties = {"app.mode=branch", "app.local-site=s2"})
@EnableConfigurationProperties(AppProperties.class)
@Import(AppModeService.class)
class SystemControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SiteHealthService health;

    @Test
    void mode_branchDeployment_returnsLocalSiteOnly() throws Exception {
        mvc.perform(get("/api/system/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("BRANCH"))
                .andExpect(jsonPath("$.localSite").value("S2"))
                .andExpect(jsonPath("$.hubSite").value("S0"))
                .andExpect(jsonPath("$.branchSites.length()").value(1))
                .andExpect(jsonPath("$.branchSites[0]").value("S2"));
    }

    @Test
    void branchApi_branchDeploymentWithOtherSiteHeader_returns400WrongSite() throws Exception {
        mvc.perform(get("/api/bac-si").header("X-Site", "S1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_SITE"));
    }
}
