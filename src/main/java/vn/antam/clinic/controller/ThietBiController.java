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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.ThietBiRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.ThietBiResponse;
import vn.antam.clinic.service.ThietBiService;

/** Thiết bị y tế của chi nhánh đang chọn (header X-Site). */
@RestController
@RequestMapping("/api/thiet-bi")
public class ThietBiController {

    private final ThietBiService service;

    public ThietBiController(ThietBiService service) {
        this.service = service;
    }

    @GetMapping
    public List<ThietBiResponse> search(@RequestParam(required = false) String q) {
        return service.search(q);
    }

    @GetMapping("/{maTB}")
    public ThietBiResponse get(@PathVariable String maTB) {
        return service.get(maTB);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ThietBiResponse create(@Valid @RequestBody ThietBiRequest request) {
        return service.create(request);
    }

    @PutMapping("/{maTB}")
    public ThietBiResponse update(@PathVariable String maTB, @Valid @RequestBody ThietBiRequest request) {
        return service.update(maTB, request);
    }

    @DeleteMapping("/{maTB}")
    public DeleteResponse delete(@PathVariable String maTB) {
        return service.delete(maTB);
    }
}
