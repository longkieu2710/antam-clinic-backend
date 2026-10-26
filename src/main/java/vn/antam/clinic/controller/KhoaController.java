package vn.antam.clinic.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.KhoaRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.KhoaResponse;
import vn.antam.clinic.service.KhoaService;

/** Khoa của chi nhánh đang chọn (header X-Site). */
@RestController
@RequestMapping("/api/khoa")
public class KhoaController {

    private final KhoaService service;

    public KhoaController(KhoaService service) {
        this.service = service;
    }

    @GetMapping
    public List<KhoaResponse> list() {
        return service.list();
    }

    @GetMapping("/{maKhoa}")
    public KhoaResponse get(@PathVariable String maKhoa) {
        return service.get(maKhoa);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KhoaResponse create(@Valid @RequestBody KhoaRequest request) {
        return service.create(request);
    }

    @PutMapping("/{maKhoa}")
    public KhoaResponse update(@PathVariable String maKhoa, @Valid @RequestBody KhoaRequest request) {
        return service.update(maKhoa, request);
    }

    @DeleteMapping("/{maKhoa}")
    public DeleteResponse delete(@PathVariable String maKhoa) {
        return service.delete(maKhoa);
    }
}
