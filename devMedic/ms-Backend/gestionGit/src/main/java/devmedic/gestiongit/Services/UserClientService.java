package devmedic.gestiongit.Services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserClientService {

    private static final String USER_SERVICE_URL = "http://gestion-user:8081";

    public Long getUserIdByKeycloakId(String keycloakId, String jwtToken) {
        return getUserByKeycloakId(keycloakId, jwtToken).getId();
    }

    public boolean isAdmin(String keycloakId, String jwtToken) {
        UserResponse user = getUserByKeycloakId(keycloakId, jwtToken);
        return user.getRoles() != null && user.getRoles().contains("ADMIN");
    }

    public UserResponse getUserByKeycloakId(String keycloakId, String jwtToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    URI.create(USER_SERVICE_URL + "/api/users/by-keycloak/" + keycloakId),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserResponse.class
            );

            if (response.getBody() != null) return response.getBody();
            throw new RuntimeException("User non trouvé");

        } catch (Exception e) {
            throw new RuntimeException("Erreur communication gestion-user: " + e.getMessage());
        }
    }

    /**
     * ✅ Récupère tous les utilisateurs (id, email, seniority, ...) pour permettre
     * de croiser les auteurs de commits (authorEmail) avec leur séniorité.
     * Utilisé par RiskAnalysisService (cahier des charges §3.8 — "modules instables
     * par séniorité").
     *
     * Si l'appel échoue (service indisponible, token invalide), on retourne une liste
     * vide plutôt que de faire échouer toute l'analyse de risque — le calcul de risque
     * continue simplement sans l'enrichissement séniorité.
     */
    public List<UserResponse> getAllUsers(String jwtToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);

            ResponseEntity<UserResponse[]> response = restTemplate.exchange(
                    URI.create(USER_SERVICE_URL + "/api/users"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserResponse[].class
            );

            if (response.getBody() != null) {
                return List.of(response.getBody());
            }
            return List.of();

        } catch (Exception e) {
            // ✅ Dégradation propre : pas de séniorité disponible, mais l'analyse continue
            return List.of();
        }
    }

    public static class UserResponse {
        private Long id;
        private String email;
        private String seniority; // ex: "JUNIOR", "MID", "SENIOR", "STAFF" ou null si non défini
        private Set<String> roles;
        private String githubToken;
        private String gitlabToken;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getSeniority() { return seniority; }
        public void setSeniority(String seniority) { this.seniority = seniority; }
        public Set<String> getRoles() { return roles; }
        public void setRoles(Set<String> roles) { this.roles = roles; }
        public String getGithubToken() { return githubToken; }
        public void setGithubToken(String githubToken) { this.githubToken = githubToken; }
        public String getGitlabToken() { return gitlabToken; }
        public void setGitlabToken(String gitlabToken) { this.gitlabToken = gitlabToken; }
    }
}