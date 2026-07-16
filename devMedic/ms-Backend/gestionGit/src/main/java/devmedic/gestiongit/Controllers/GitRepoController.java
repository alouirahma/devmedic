package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.GitRepository;
import devmedic.gestiongit.Entities.ProviderType;
import devmedic.gestiongit.Services.GitRepoServ;
import devmedic.gestiongit.Services.UserClientService;
import devmedic.gestiongit.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git/repositories")
@RequiredArgsConstructor
public class GitRepoController {

    private final GitRepoServ repositoryService;
    private final UserClientService userClientService;

    @GetMapping
    public ResponseEntity<List<GitRepository>> getMyRepositories(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(repositoryService.getAll());
        return ResponseEntity.ok(repositoryService.getByUserId(resolveUserId(authHeader)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GitRepository> getById(@PathVariable Long id) {
        return ResponseEntity.ok(repositoryService.getById(id));
    }

    @GetMapping("/provider/{provider}")
    public ResponseEntity<List<GitRepository>> getByProvider(
            @PathVariable ProviderType provider,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(repositoryService.getByProvider(provider));
        return ResponseEntity.ok(repositoryService.getByUserIdAndProvider(resolveUserId(authHeader), provider));
    }

    @GetMapping("/stats/total")
    public ResponseEntity<Map<String, Long>> getTotalCount(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        long count = isAdmin(authHeader)
                ? repositoryService.getTotalCount()
                : repositoryService.getByUserId(resolveUserId(authHeader)).size();
        return ResponseEntity.ok(Map.of("total", count));
    }

    @GetMapping("/stats/by-provider")
    public ResponseEntity<Map<String, Long>> getCountByProvider(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(repositoryService.getCountByProviderForUser(null));
        return ResponseEntity.ok(repositoryService.getCountByProviderForUser(resolveUserId(authHeader)));
    }

    @GetMapping("/stats/by-visibility")
    public ResponseEntity<Map<String, Long>> getCountByVisibility(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(repositoryService.getCountByVisibilityForUser(null));
        return ResponseEntity.ok(repositoryService.getCountByVisibilityForUser(resolveUserId(authHeader)));
    }

    @GetMapping("/stats/recently-analyzed")
    public ResponseEntity<List<GitRepository>> getRecentlyAnalyzed(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "10") int limit) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(repositoryService.getRecentlyAnalyzed(limit));
        return ResponseEntity.ok(repositoryService.getRecentlyAnalyzedForUser(resolveUserId(authHeader), limit));
    }

    private Long resolveUserId(String authHeader) {
        if (authHeader == null) return 1L;
        String keycloakId = JwtUtil.extractKeycloakId(authHeader);
        if (keycloakId == null) return 1L;
        return userClientService.getUserIdByKeycloakId(keycloakId, authHeader.replace("Bearer ", ""));
    }

    private boolean isAdmin(String authHeader) {
        if (authHeader == null) return false;
        return JwtUtil.isAdmin(authHeader);
    }
}