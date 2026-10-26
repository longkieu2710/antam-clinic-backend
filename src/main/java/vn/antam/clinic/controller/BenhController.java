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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.BenhRequest;
import vn.antam.clinic.dto.response.BenhResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.service.BenhService;

/**
 * Bệnh (ICD-10) — danh mục chung.
 * <ul>
 *   <li>{@code /api/master/benh}: đọc/ghi tại S0; Logical Replication tự đẩy thay đổi xuống S1..S3.</li>
 *   <li>{@code /api/benh}: đọc bản sao ở chi nhánh đang chọn.</li>
 * </ul>
 */
@RestController
public class BenhController {

    private final BenhService service;

    public BenhController(BenhService service) {
        this.service = service;
    }

    @GetMapping("/api/master/benh")
    public List<BenhResponse> listMaster(@RequestParam(required = false) String q) {
        return service.listMaster(q);
    }

    @PostMapping("/api/master/benh")
    @ResponseStatus(HttpStatus.CREATED)
    public BenhResponse create(@Valid @RequestBody BenhRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/master/benh/{maBenh}")
    public BenhResponse update(@PathVariable String maBenh, @Valid @RequestBody BenhRequest request) {
        return service.update(maBenh, request);
    }

    @DeleteMapping("/api/master/benh/{maBenh}")
    public DeleteResponse delete(@PathVariable String maBenh) {
        return service.delete(maBenh);
    }

    @GetMapping("/api/benh")
    public List<BenhResponse> listReplica(@RequestParam(required = false) String q) {
        return service.listReplica(q);
    }
}
