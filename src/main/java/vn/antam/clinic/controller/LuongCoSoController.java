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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.LuongCoSoRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.LuongCoSoResponse;
import vn.antam.clinic.service.LuongCoSoService;

/** Lương cơ sở — danh mục chung, đọc/ghi tại S0 (Logical Replication đẩy xuống S1..S3). */
@RestController
@RequestMapping("/api/master/luong-co-so")
public class LuongCoSoController {

    private final LuongCoSoService service;

    public LuongCoSoController(LuongCoSoService service) {
        this.service = service;
    }

    @GetMapping
    public List<LuongCoSoResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LuongCoSoResponse create(@Valid @RequestBody LuongCoSoRequest request) {
        return service.create(request);
    }

    /** @param ngayApDung dạng yyyy-MM-dd */
    @PutMapping("/{ngayApDung}")
    public LuongCoSoResponse update(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngayApDung,
                                    @Valid @RequestBody LuongCoSoRequest request) {
        return service.update(ngayApDung, request);
    }

    @DeleteMapping("/{ngayApDung}")
    public DeleteResponse delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngayApDung) {
        return service.delete(ngayApDung);
    }
}
