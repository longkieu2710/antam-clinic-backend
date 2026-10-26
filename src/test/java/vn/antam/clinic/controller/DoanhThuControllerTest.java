package vn.antam.clinic.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.response.DoanhThuResponse;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.service.DoanhThuService;

@WebMvcTest(DoanhThuController.class)
@EnableConfigurationProperties(AppProperties.class)
class DoanhThuControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DoanhThuService service;

    @Test
    void doanhThu_isoDates_areParsed() throws Exception {
        LocalDate tu = LocalDate.of(2026, 9, 1);
        LocalDate den = LocalDate.of(2026, 9, 30);
        when(service.doanhThu(tu, den)).thenReturn(new DoanhThuResponse(tu, den, 5, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("4"), List.of(), List.of()));

        mvc.perform(get("/api/bao-cao/doanh-thu").param("tu", "2026-09-01").param("den", "2026-09-30")
                        .header("X-Site", "S1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soLuot").value(5))
                .andExpect(jsonPath("$.tongDoanhThu").value(4));
    }

    @Test
    void doanhThu_badRange_returns400() throws Exception {
        when(service.doanhThu(any(), any()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "BAD_RANGE", "sai khoảng"));

        mvc.perform(get("/api/bao-cao/doanh-thu").param("tu", "2026-09-30").param("den", "2026-09-01")
                        .header("X-Site", "S1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_RANGE"));
    }
}
