package in.sslexample.springssl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.web.client.RestClient;


@Configuration 
public class RestClientConfig {
    
    @Bean("defaultRestClient")
    public RestClient restClient(RestClient.Builder builder) {
        return builder.clone().baseUrl("https://api.restful-api.dev").build();
    }

    @Bean("customRestClient")
    public RestClient customRestClient(
            RestClient.Builder builder,
            RestClientSsl ssl) {

        return builder.clone()
                .baseUrl("https://api.restful-api.dev")
                .apply(ssl.fromBundle("restful-api"))
                .build();
    }
}
