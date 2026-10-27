package com.junaid.recon.api;

import com.junaid.recon.core.MatchStatus;
import com.junaid.recon.persistence.ReconciliationRun;
import com.junaid.recon.service.ReconciliationService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    /** Upload a bank CSV and a ledger CSV; get back the summary and every exception. */
    @PostMapping(path = "/reconciliations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RunResponse reconcile(@RequestPart("bank") MultipartFile bank,
                                 @RequestPart("ledger") MultipartFile ledger) throws IOException {
        if (bank.isEmpty() || ledger.isEmpty()) {
            throw new IllegalArgumentException("Both bank and ledger files are required and must not be empty");
        }
        try (InputStream b = bank.getInputStream(); InputStream l = ledger.getInputStream()) {
            return service.runBatch(b, l);
        }
    }

    @GetMapping("/reconciliations")
    public List<ReconciliationRun> recentRuns() {
        return service.recentRuns();
    }

    @GetMapping("/reconciliations/{runId}")
    public RunResponse getRun(@PathVariable String runId) {
        return service.getRun(runId);
    }

    /** Items needing review across all runs. Optional status filter, paged. */
    @GetMapping("/exceptions")
    public Map<String, Object> exceptions(@RequestParam(required = false) MatchStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        Page<ResultView> result = service.exceptions(status, page, size);
        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalItems", result.getTotalElements(),
                "totalPages", result.getTotalPages());
    }
}
