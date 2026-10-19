package in.sslexample.springssl.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service 
public class ExternalApiService {
    private final RestClient defaultRestClient;
    private final RestClient customRestClient;

    public ExternalApiService(
            @Qualifier("defaultRestClient")
            RestClient defaultRestClient,
            @Qualifier("customRestClient")
            RestClient customRestClient) {

        this.defaultRestClient = defaultRestClient;
        this.customRestClient = customRestClient;
    }

    public String callDefault() {
        return fetch(defaultRestClient);
    }

    public String callCustom() {
        return fetch(customRestClient);
    }

    private String fetch(RestClient client) {
        return client.get()
                .uri("/objects/7")
                .retrieve()
                .body(String.class);
    } 
    
}
