package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.GitRepository;
import devmedic.gestiongit.Services.GitHubService;
import devmedic.gestiongit.Services.GitLabService;
import devmedic.gestiongit.Services.UserClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git/sync")
@RequiredArgsConstructor
public class GitSyncController {

    private final GitHubService gitHubService;
    private final GitLabService gitLabService;
    private final UserClientService userClientService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> sync(
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        String rawToken   = jwt.getTokenValue();
        Long userId       = userClientService.getUserIdByKeycloakId(keycloakId, rawToken);

        // ✅ Lire les tokens GitHub/GitLab directement depuis les claims Keycloak
        String githubToken = jwt.getClaimAsString("githubToken");
        String gitlabToken = jwt.getClaimAsString("gitlabToken");

        List<GitRepository> all = new ArrayList<>();
        List<String> errors     = new ArrayList<>();

        if (githubToken != null && !githubToken.isBlank()) {
            try {
                all.addAll(gitHubService.importRepositories(userId, githubToken));
            } catch (Exception e) {
                errors.add("GitHub: " + e.getMessage());
            }
        }

        if (gitlabToken != null && !gitlabToken.isBlank()) {
            try {
                all.addAll(gitLabService.importRepositories(userId, gitlabToken));
            } catch (Exception e) {
                errors.add("GitLab: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "repositoriesImported", all.size(),
                "errors", errors
        ));
    }
}