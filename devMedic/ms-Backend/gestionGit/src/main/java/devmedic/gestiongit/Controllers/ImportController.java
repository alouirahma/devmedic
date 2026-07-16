package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.GitRepository;
import devmedic.gestiongit.JwtUtil;
import devmedic.gestiongit.Services.ContributionService;
import devmedic.gestiongit.Services.GitHubService;
import devmedic.gestiongit.Services.GitLabService;
import devmedic.gestiongit.Services.RiskAnalysisService;
import devmedic.gestiongit.Services.SonarQubeService;
import devmedic.gestiongit.Services.UserClientService;
import devmedic.gestiongit.Repos.GitRepositoryRep;
import devmedic.gestiongit.Entities.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git/import")
@RequiredArgsConstructor
public class ImportController {

    private final GitHubService gitHubService;
    private final GitLabService gitLabService;
    private final UserClientService userClientService;
    private final GitRepositoryRep gitRepositoryRep;
    private final ContributionService contributionService;
    private final RiskAnalysisService riskAnalysisService; // ✅ §3.8 — score de stabilité / hotspots
    private final SonarQubeService sonarQubeService;        // ✅ §3.7 — qualité du code

    @PostMapping("/github")
    public ResponseEntity<Map<String, Object>> importGitHub(
            @RequestParam String token,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt);
        List<GitRepository> repos = gitHubService.importRepositories(userId, token);
        return ResponseEntity.ok(Map.of(
                "message", "Import GitHub terminé avec succès",
                "repositoriesImported", repos.size()
        ));
    }

    @PostMapping("/gitlab")
    public ResponseEntity<Map<String, Object>> importGitLab(
            @RequestParam String token,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt);
        List<GitRepository> repos = gitLabService.importRepositories(userId, token);
        return ResponseEntity.ok(Map.of(
                "message", "Import GitLab terminé avec succès",
                "repositoriesImported", repos.size()
        ));
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeRepositories(
            @RequestBody AnalyzeRequest body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token manquant"));
        }

        String githubToken = null;
        String gitlabToken = null;

        // JWT brut pour appeler gestion-user (réutilisé aussi pour le calcul de risque
        // par séniorité — RiskAnalysisService.analyze a besoin de ce token pour
        // récupérer la liste des utilisateurs avec leur séniorité)
        String jwtToken = authHeader.replace("Bearer ", "").trim();

        try {

            // Récupération du Keycloak ID depuis le JWT
            String keycloakId = JwtUtil.extractKeycloakId(authHeader);

            // Appel automatique du microservice gestion-user
            UserClientService.UserResponse userInfo =
                    userClientService.getUserByKeycloakId(keycloakId, jwtToken);

            if (userInfo != null) {
                githubToken = userInfo.getGithubToken();
                gitlabToken = userInfo.getGitlabToken();
            }

            System.out.println("=== TOKENS RECUPERES DE GESTION-USER ===");
            System.out.println("githubToken : " +
                    (githubToken != null ? "***présent***" : "null"));
            System.out.println("gitlabToken : " +
                    (gitlabToken != null ? "***présent***" : "null"));

        } catch (Exception e) {
            System.err.println("Erreur récupération tokens : " + e.getMessage());
        }

        // Fallback depuis le body
        if ((githubToken == null || githubToken.isBlank())
                && body.getGithubToken() != null) {
            githubToken = body.getGithubToken();
        }

        if ((gitlabToken == null || gitlabToken.isBlank())
                && body.getGitlabToken() != null) {
            gitlabToken = body.getGitlabToken();
        }

        List<Long> repositoryIds = body.getRepositoryIds();

        if (repositoryIds == null || repositoryIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Aucun repository sélectionné"));
        }

        List<GitRepository> repos = gitRepositoryRep.findAllById(repositoryIds);

        List<String> errors = new ArrayList<>();
        // ✅ Résultats du calcul de risque par repo (§3.8), renvoyés au frontend
        Map<String, Object> riskResults = new HashMap<>();
        // ✅ Résultats de l'analyse qualité par repo (§3.7), renvoyés au frontend
        Map<String, Object> qualityResults = new HashMap<>();
        int count = 0;

        for (GitRepository repo : repos) {

            try {

                if (repo.getProvider() == ProviderType.GITHUB
                        && githubToken != null
                        && !githubToken.isBlank()) {

                    gitHubService.importBranches(repo, githubToken);
                    gitHubService.importTags(repo, githubToken);
                    gitHubService.importCommits(repo, githubToken);
                    gitHubService.importPullRequests(repo, githubToken);
                    gitHubService.importPushes(repo, githubToken);

                    contributionService.calculateContributions(repo);

                    // ✅ §3.8 — calcul du score de stabilité / hotspots juste après
                    // l'import, pendant que les données sont fraîches en base.
                    safeComputeRisk(repo, jwtToken, riskResults);

                    // ✅ §3.7 — scan SonarQube. Isolé dans son propre try/catch :
                    // un échec (timeout, repo trop volumineux, Sonar indisponible)
                    // ne doit jamais faire échouer l'analyse Git globale.
                    String cloneUrl = buildGitHubCloneUrl(repo, githubToken);
                    safeComputeQuality(repo, cloneUrl, qualityResults);

                    repo.updateLastAnalyzed();
                    gitRepositoryRep.save(repo);

                    count++;

                } else if (repo.getProvider() == ProviderType.GITLAB
                        && gitlabToken != null
                        && !gitlabToken.isBlank()) {

                    gitLabService.importBranches(repo, gitlabToken);
                    gitLabService.importTags(repo, gitlabToken);
                    gitLabService.importCommits(repo, gitlabToken);
                    gitLabService.importPullRequests(repo, gitlabToken);
                    gitLabService.importPushes(repo, gitlabToken);

                    contributionService.calculateContributions(repo);

                    // ✅ §3.8 — idem pour GitLab
                    safeComputeRisk(repo, jwtToken, riskResults);

                    // ✅ §3.7 — idem pour GitLab
                    String cloneUrl = buildGitLabCloneUrl(repo, gitlabToken);
                    safeComputeQuality(repo, cloneUrl, qualityResults);

                    repo.updateLastAnalyzed();
                    gitRepositoryRep.save(repo);

                    count++;

                } else {

                    errors.add(
                            repo.getName()
                                    + ": token non disponible pour "
                                    + repo.getProvider()
                    );
                }

            } catch (Exception e) {

                System.err.println(
                        ">>> Erreur repo "
                                + repo.getName()
                                + ": "
                                + e.getMessage()
                );

                errors.add(
                        repo.getName()
                                + ": "
                                + e.getMessage()
                );
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("repositoriesAnalyzed", count);
        response.put("errors", errors);
        response.put("riskResults", riskResults);       // ✅ §3.8
        response.put("qualityResults", qualityResults); // ✅ §3.7

        return ResponseEntity.ok(response);
    }

    /**
     * Calcule le score de risque pour un repo et l'ajoute aux résultats à renvoyer.
     * Isolé dans sa propre méthode avec son propre try/catch : si ce calcul échoue
     * (ex: gestion-user indisponible pour la séniorité), l'analyse Git du repo
     * (commits, PR, pushes déjà importés et sauvegardés) reste un succès.
     */
    private void safeComputeRisk(GitRepository repo, String jwtToken, Map<String, Object> riskResults) {
        try {
            RiskAnalysisService.RiskAnalysisResult risk = riskAnalysisService.analyze(repo.getId(), jwtToken);
            riskResults.put(repo.getName(), risk);
        } catch (Exception e) {
            System.err.println(">>> Erreur calcul risque pour " + repo.getName() + ": " + e.getMessage());
            // On ne propage pas l'erreur : le calcul de risque est un enrichissement,
            // pas une condition de succès de l'analyse Git elle-même.
        }
    }

    /**
     * Lance le scan SonarQube pour un repo et ajoute le résultat aux résultats à
     * renvoyer. Isolé de la même façon que safeComputeRisk : un échec du scan
     * (timeout, token Sonar manquant, repo vide) n'invalide jamais l'analyse Git.
     */
    private void safeComputeQuality(GitRepository repo, String cloneUrl, Map<String, Object> qualityResults) {
        try {
            SonarQubeService.QualityAnalysisResult quality = sonarQubeService.analyze(repo, cloneUrl);
            qualityResults.put(repo.getName(), quality);
        } catch (Exception e) {
            System.err.println(">>> Erreur scan qualité (Sonar) pour " + repo.getName() + ": " + e.getMessage());
        }
    }

    /** Construit l'URL de clone HTTPS authentifiée pour GitHub. */
    private String buildGitHubCloneUrl(GitRepository repo, String token) {
        return "https://" + token + "@github.com/" + repo.getOwner() + "/" + repo.getName() + ".git";
    }

    /** Construit l'URL de clone HTTPS authentifiée pour GitLab. */
    private String buildGitLabCloneUrl(GitRepository repo, String token) {
        return "https://oauth2:" + token + "@gitlab.com/" + repo.getOwner() + "/" + repo.getName() + ".git";
    }

    public static class AnalyzeRequest {
        private List<Long> repositoryIds;
        private String githubToken;
        private String gitlabToken;

        public List<Long> getRepositoryIds() { return repositoryIds; }
        public void setRepositoryIds(List<Long> repositoryIds) { this.repositoryIds = repositoryIds; }
        public String getGithubToken() { return githubToken; }
        public void setGithubToken(String githubToken) { this.githubToken = githubToken; }
        public String getGitlabToken() { return gitlabToken; }
        public void setGitlabToken(String gitlabToken) { this.gitlabToken = gitlabToken; }
    }

    private Long resolveUserId(Jwt jwt) {
        if (jwt == null) return 1L;
        String keycloakId = jwt.getSubject();
        String rawToken = jwt.getTokenValue();
        return userClientService.getUserIdByKeycloakId(keycloakId, rawToken);
    }
}