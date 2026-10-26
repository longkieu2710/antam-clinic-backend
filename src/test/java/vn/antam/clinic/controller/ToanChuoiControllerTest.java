package vn.antam.clinic.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.service.ToanChuoiService;

@WebMvcTest(ToanChuoiController.class)
@EnableConfigurationProperties(AppProperties.class)
class ToanChuoiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ToanChuoiService service;

    @Test
    void chiNhanh_commaSeparatedCodes_runsAtHubWithoutSiteHeader() throws Exception {
        when(service.chiNhanhCoHoSo(List.of("BN01", "BN02"))).thenReturn(Map.of("BN01", List.of("CN1", "CN3")));

        mvc.perform(get("/api/hq/benh-nhan/chi-nhanh").param("ma", "BN01,BN02"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Site-Used", "S0"))
                .andExpect(jsonPath("$.BN01[1]").value("CN3"));
    }

    @Test
    void thongKeBenh_badMonth_returns400() throws Exception {
        mvc.perform(get("/api/hq/thong-ke-benh").param("thang", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_PARAM"));
    }

    @Test
    void thongKeBenh_validMonth_passesYearMonth() throws Exception {
        mvc.perform(get("/api/hq/thong-ke-benh").param("thang", "2026-09")).andExpect(status().isOk());

        verify(service).thongKeBenh(YearMonth.of(2026, 9));
    }
}
