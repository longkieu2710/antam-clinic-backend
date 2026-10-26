package vn.antam.clinic.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.response.ThongKeBenhItemResponse;
import vn.antam.clinic.dto.response.ThongKeBenhResponse;
import vn.antam.clinic.service.ThongKeBenhService;

@WebMvcTest(ThongKeBenhController.class)
@EnableConfigurationProperties(AppProperties.class)
class ThongKeBenhControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ThongKeBenhService service;

    @Test
    void thongKe_monthParam_isParsedAsYearMonth() throws Exception {
        when(service.thongKe(YearMonth.of(2026, 9))).thenReturn(new ThongKeBenhResponse("2026-09", 2,
                List.of(new ThongKeBenhItemResponse("B01", "Viêm họng cấp tính", "J02", 2, 2))));

        mvc.perform(get("/api/bao-cao/thong-ke-benh").param("thang", "2026-09").header("X-Site", "S1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benh[0].soLanMac").value(2));
        verify(service).thongKe(YearMonth.of(2026, 9));
    }

    @Test
    void thongKe_invalidMonth_returns400() throws Exception {
        mvc.perform(get("/api/bao-cao/thong-ke-benh").param("thang", "2026-13").header("X-Site", "S1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_PARAM"));
        verifyNoInteractions(service);
    }
}
