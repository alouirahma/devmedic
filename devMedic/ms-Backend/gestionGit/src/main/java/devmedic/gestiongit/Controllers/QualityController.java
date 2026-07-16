package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.GitRepository;
import devmedic.gestiongit.Entities.ProviderType;
import devmedic.gestiongit.Entities.QualityMetric;
import devmedic.gestiongit.JwtUtil;
import devmedic.gestiongit.Repos.GitRepositoryRep;
import devmedic.gestiongit.Services.SonarQubeService;
import devmedic.gestiongit.Services.UserClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/git/quality")
@RequiredArgsConstructor
public class QualityController {

    private final SonarQubeService sonarQubeService;
    private final GitRepositoryRep gitRepositoryRep;
    private final UserClientService userClientService;

    @GetMapping("/repository/{repoId}")
    public ResponseEntity<?> getLatest(@PathVariable Long repoId) {
        Optional<QualityMetric> latest = sonarQubeService.getLatest(repoId);

        if (latest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "message", "Aucune analyse de qualité disponible pour ce repository. Lancez une analyse d'abord.",
                    "duplicationPercent", 0,
                    "complexity", 0,
                    "maintainabilityIndex", 0,
                    "codeSmells", 0
            ));
        }

        QualityMetric metric = latest.get();
        return ResponseEntity.ok(Map.of(
                "duplicationPercent", metric.getDuplicationPercent(),
                "complexity", metric.getComplexity(),
                "maintainabilityIndex", metric.getMaintainabilityIndex(),
                "codeSmells", metric.getCodeSmells(),
                "calculatedAt", metric.getCalculatedAt()
        ));
    }

    @PostMapping("/repository/{repoId}/analyze")
    public ResponseEntity<?> analyze(
            @PathVariable Long repoId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token manquant"));
        }

        String jwtToken = authHeader.replace("Bearer ", "").trim();

        Optional<GitRepository> repoOpt = gitRepositoryRep.findById(repoId);
        if (repoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Repository introuvable: " + repoId));
        }
        GitRepository repo = repoOpt.get();

        // ✅ Récupération automatique du token GitHub/GitLab depuis gestion-user,
        // exactement comme ImportController — le frontend n'a rien à fournir.
        String githubToken = null;
        String gitlabToken = null;
        try {
            String keycloakId = JwtUtil.extractKeycloakId(authHeader);
            UserClientService.UserResponse userInfo =
                    userClientService.getUserByKeycloakId(keycloakId, jwtToken);
            if (userInfo != null) {
                githubToken = userInfo.getGithubToken();
                gitlabToken = userInfo.getGitlabToken();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Impossible de récupérer le token utilisateur: " + e.getMessage()));
        }

        try {
            String cloneUrl;

            if (repo.getProvider() == ProviderType.GITHUB) {
                if (githubToken == null || githubToken.isBlank()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Aucun token GitHub associé à cet utilisateur"));
                }
                cloneUrl = buildGitHubCloneUrl(repo, githubToken);

            } else if (repo.getProvider() == ProviderType.GITLAB) {
                if (gitlabToken == null || gitlabToken.isBlank()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Aucun token GitLab associé à cet utilisateur"));
                }
                cloneUrl = buildGitLabCloneUrl(repo, gitlabToken);

            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Provider non supporté: " + repo.getProvider()));
            }

            SonarQubeService.QualityAnalysisResult result = sonarQubeService.analyze(repo, cloneUrl);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Échec de l'analyse qualité: " + e.getMessage()));
        }
    }

    private String buildGitHubCloneUrl(GitRepository repo, String token) {
        return "https://" + token + "@github.com/" + repo.getOwner() + "/" + repo.getName() + ".git";
    }

    private String buildGitLabCloneUrl(GitRepository repo, String token) {
        return "https://oauth2:" + token + "@gitlab.com/" + repo.getOwner() + "/" + repo.getName() + ".git";
    }
}