package vn.antam.clinic.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.ChanDoanRequest;
import vn.antam.clinic.dto.request.LuotChuaRequest;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.dto.request.LuotKhamRequest;
import vn.antam.clinic.dto.request.SuaLuotChuaRequest;
import vn.antam.clinic.dto.request.SuaLuotKhamRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.LuotChiTietResponse;
import vn.antam.clinic.dto.response.LuotResponse;
import vn.antam.clinic.dto.response.TaoLuotResponse;
import vn.antam.clinic.service.LuotChuaService;
import vn.antam.clinic.service.LuotKhamService;
import vn.antam.clinic.service.LuotService;

/** Lượt khám / chữa của chi nhánh đang chọn — gửi kèm header X-Site: S1 | S2 | S3. */
@RestController
public class LuotController {

    private final LuotService luotService;
    private final LuotKhamService luotKhamService;
    private final LuotChuaService luotChuaService;

    public LuotController(LuotService luotService, LuotKhamService luotKhamService, LuotChuaService luotChuaService) {
        this.luotService = luotService;
        this.luotKhamService = luotKhamService;
        this.luotChuaService = luotChuaService;
    }

    /**
     * @param tu   yyyy-MM-dd (tính cả ngày này)
     * @param den  yyyy-MM-dd (tính cả ngày này)
     * @param loai KHAM | CHUA
     * @param q    mã lượt, mã hoặc họ tên bệnh nhân
     */
    @GetMapping("/api/luot")
    public List<LuotResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tu,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate den,
            @RequestParam(required = false) String loai,
            @RequestParam(required = false) String khoa,
            @RequestParam(required = false) String bs,
            @RequestParam(required = false) String q) {
        return luotService.search(tu, den, loai, khoa, bs, q);
    }

    @GetMapping("/api/luot/{maLuot}")
    public LuotChiTietResponse get(@PathVariable String maLuot) {
        return luotService.get(maLuot);
    }

    /** Xoá lượt nhập nhầm (chỉ lượt trong tháng, lần chữa cuối của đợt / lượt khám chưa có lần chữa). */
    @DeleteMapping("/api/luot/{maLuot}")
    public DeleteResponse delete(@PathVariable String maLuot) {
        return luotService.delete(maLuot);
    }

    @PostMapping("/api/luot-kham")
    @ResponseStatus(HttpStatus.CREATED)
    public TaoLuotResponse createKham(@Valid @RequestBody LuotKhamRequest request) {
        return luotKhamService.create(request);
    }

    /** Sửa kết quả lượt chữa; "Khỏi" kết thúc đợt (BR10). 409 khi đợt đã kết thúc / "Khỏi" không ở lần chữa cuối. */
    @PutMapping("/api/luot-chua/{maLuot}")
    public LuotChiTietResponse suaChua(@PathVariable String maLuot, @Valid @RequestBody SuaLuotChuaRequest request) {
        return luotService.suaLuotChua(maLuot, request);
    }

    /** Sửa kết luận + ngày hẹn tái khám của lượt khám (409 DOT_DA_KET_THUC khi có đợt đã khỏi / ngừng điều trị). */
    @PutMapping("/api/luot-kham/{maLuot}")
    public LuotChiTietResponse suaKham(@PathVariable String maLuot, @Valid @RequestBody SuaLuotKhamRequest request) {
        return luotService.suaLuotKham(maLuot, request);
    }

    @PostMapping("/api/luot-chua")
    @ResponseStatus(HttpStatus.CREATED)
    public TaoLuotResponse createChua(@Valid @RequestBody LuotChuaRequest request) {
        return luotChuaService.create(request);
    }

    @PostMapping("/api/luot-kham/{maLuot}/chan-doan")
    @ResponseStatus(HttpStatus.CREATED)
    public DotDieuTriResponse themChanDoan(@PathVariable String maLuot, @Valid @RequestBody ChanDoanRequest request) {
        return luotKhamService.themChanDoan(maLuot, request);
    }

}
