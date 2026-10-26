package vn.antam.clinic.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.service.LuongService;

@WebMvcTest(LuongController.class)
@EnableConfigurationProperties(AppProperties.class)
class LuongControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LuongService service;

    @Test
    void bangLuong_noBaseSalary_returns422() throws Exception {
        when(service.bangLuong(YearMonth.of(2020, 1)))
                .thenThrow(new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_BASE_SALARY", "chưa có"));

        mvc.perform(get("/api/bao-cao/luong").param("thang", "2020-01").header("X-Site", "S2"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_BASE_SALARY"))
                .andExpect(jsonPath("$.site").value("S2"));
    }

    @Test
    void bangLuong_missingSite_returns400() throws Exception {
        mvc.perform(get("/api/bao-cao/luong"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SITE"));
        verifyNoInteractions(service);
    }
}
