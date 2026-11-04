package web.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @Bean
    RestTemplate flowableRestTemplate(
            RestTemplateBuilder builder,
            @Value("${flowable.rest.username}") String username,
            @Value("${flowable.rest.password}") String password) {
        return builder.basicAuthentication(username, password).build();
    }
}
