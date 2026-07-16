package devmedic.gestionuser;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableDiscoveryClient
@OpenAPIDefinition(
        info = @Info(
                title = "Gestion User API",
                version = "1.0",
                description = "API de gestion des utilisateurs avec Keycloak"
        )
)
public class GestionUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestionUserApplication.class, args);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // Sécurité appliquée à tous les endpoints
                .addSecurityItem(new SecurityRequirement().addList("oauth2"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        // ── OAuth2 Authorization Code (login automatique) ──
                        .addSecuritySchemes("oauth2",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .description("Login via Keycloak (GitHub/GitLab)")
                                        .flows(new OAuthFlows()
                                                .authorizationCode(new OAuthFlow()
                                                        .authorizationUrl("http://auth.localhost/realms/devmedic/protocol/openid-connect/auth")
                                                        .tokenUrl("http://auth.localhost/realms/devmedic/protocol/openid-connect/token")
                                                        .scopes(new Scopes()
                                                                .addString("openid", "OpenID")
                                                                .addString("profile", "Profile")
                                                                .addString("email", "Email")
                                                        )
                                                )
                                        )
                        )
                        // ── Bearer JWT (token manuel) ──
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Colle ton token JWT ici")
                        )
                );
    }
}