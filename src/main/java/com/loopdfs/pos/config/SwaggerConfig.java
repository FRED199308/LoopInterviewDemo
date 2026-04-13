package com.loopdfs.pos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("NCBA POS Integration API")
                        .description("""
                                REST API for the NCBA POS Integration Service.
                                
                                This service:
                                - Accepts a country name via POST
                                - Converts it to sentence case
                                - Calls a SOAP endpoint to retrieve the ISO code
                                - Calls a second SOAP endpoint to retrieve full country information
                                - Persists the data in MySQL
                                - Exposes full CRUD operations on the stored country data
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("NCBA POS Team")
                                .email("pos-team@ncba.co.ke")
                                .url("https://www.ncba.co.ke"))
                        .license(new License()
                                .name("Private – NCBA Internal Use Only")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server"),
                        new Server()
                                .url("https://pos-api.ncba.co.ke")
                                .description("Production server")))
                .tags(List.of(
                        new Tag()
                                .name("Countries")
                                .description("Fetch country info via SOAP and manage stored records")));
    }
}