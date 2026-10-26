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
import vn.antam.clinic.dto.request.BacSiRequest;
import vn.antam.clinic.dto.response.BacSiResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.service.BacSiService;

/** Bác sĩ của chi nhánh đang chọn — gửi kèm header X-Site: S1 | S2 | S3. */
@RestController
@RequestMapping("/api/bac-si")
public class BacSiController {

    private final BacSiService service;

    public BacSiController(BacSiService service) {
        this.service = service;
    }

    @GetMapping
    public List<BacSiResponse> search(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String khoa) {
        return service.search(q, khoa);
    }

    @GetMapping("/{maNV}")
    public BacSiResponse get(@PathVariable String maNV) {
        return service.get(maNV);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BacSiResponse create(@Valid @RequestBody BacSiRequest request) {
        return service.create(request);
    }

    @PutMapping("/{maNV}")
    public BacSiResponse update(@PathVariable String maNV, @Valid @RequestBody BacSiRequest request) {
        return service.update(maNV, request);
    }

    @DeleteMapping("/{maNV}")
    public DeleteResponse delete(@PathVariable String maNV) {
        return service.delete(maNV);
    }
}
