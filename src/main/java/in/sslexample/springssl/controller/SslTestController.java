package in.sslexample.springssl.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import in.sslexample.springssl.service.ExternalApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

@RestController
public class SslTestController {
    private final ExternalApiService service;
    private static final Logger log = LoggerFactory.getLogger(SslTestController.class);

    public SslTestController(ExternalApiService service) {
        this.service = service;
    }

    @GetMapping("/api/test/default")
    public ResponseEntity<?> testDefault() {
        return execute(false);
    }

    @GetMapping("/api/test/custom")
    public ResponseEntity<?> testCustom() {
        return execute(true);
    }

    private ResponseEntity<?> execute(boolean custom) {
        try {
            String response = custom
                    ? service.callCustom()
                    : service.callDefault();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "trustMode", custom ? "CUSTOM" : "DEFAULT",
                    "response", response
            ));

        } catch (RestClientException ex) {
            log.error("HTTPS call failed. Trust mode: {}",
            custom ? "CUSTOM" : "DEFAULT", ex);

    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of(
                    "status", "FAILED",
                    "trustMode", custom ? "CUSTOM" : "DEFAULT",
                    "exception", ex.getClass().getSimpleName()
            ));
        }
    }
}
