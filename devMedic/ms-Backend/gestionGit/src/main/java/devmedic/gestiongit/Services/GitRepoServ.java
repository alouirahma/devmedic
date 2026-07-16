package devmedic.gestiongit.Services;

import devmedic.gestiongit.Entities.GitRepository;
import devmedic.gestiongit.Entities.ProviderType;
import devmedic.gestiongit.Repos.GitRepositoryRep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GitRepoServ {

    private final GitRepositoryRep gitRepositoryRep;

    public List<GitRepository> getAll() {
        return gitRepositoryRep.findAll();
    }

    public GitRepository getById(Long id) {
        return gitRepositoryRep.findById(id)
                .orElseThrow(() -> new RuntimeException("Repository not found"));
    }

    // ✅ Cherche par userIds (liste)
    public List<GitRepository> getByUserId(Long userId) {
        return gitRepositoryRep.findByUserIdsContaining(userId);
    }

    public List<GitRepository> getByProvider(ProviderType provider) {
        return gitRepositoryRep.findByProvider(provider);
    }

    public List<GitRepository> getByUserIdAndProvider(Long userId, ProviderType provider) {
        return gitRepositoryRep.findByUserIdsContainingAndProvider(userId, provider);
    }

    public long getTotalCount() {
        return gitRepositoryRep.count();
    }

    public Map<String, Long> getCountByProviderForUser(Long userId) {
        // ✅ userId null = admin = tous les repos
        List<GitRepository> repos = (userId == null)
                ? gitRepositoryRep.findAll()
                : gitRepositoryRep.findByUserIdsContaining(userId);

        return repos.stream()
                .collect(Collectors.groupingBy(
                        repo -> repo.getProvider().toString(),
                        Collectors.counting()
                ));
    }

    public Map<String, Long> getCountByVisibilityForUser(Long userId) {
        // ✅ userId null = admin = tous les repos
        List<GitRepository> repos = (userId == null)
                ? gitRepositoryRep.findAll()
                : gitRepositoryRep.findByUserIdsContaining(userId);

        return repos.stream()
                .collect(Collectors.groupingBy(
                        repo -> repo.isPrivate() ? "private" : "public",
                        Collectors.counting()
                ));
    }

    public List<GitRepository> getRecentlyAnalyzed(int limit) {
        return gitRepositoryRep.findTop10ByOrderByLastAnalyzedAtDesc()
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<GitRepository> getRecentlyAnalyzedForUser(Long userId, int limit) {
        return gitRepositoryRep.findByUserIdsContaining(userId).stream()
                .filter(repo -> repo.getLastAnalyzedAt() != null)
                .sorted((r1, r2) -> r2.getLastAnalyzedAt().compareTo(r1.getLastAnalyzedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Map<Long, Long> getRepositoriesByUser() {
        List<GitRepository> repos = gitRepositoryRep.findAll();
        Map<Long, Long> stats = new HashMap<>();
        for (GitRepository repo : repos) {
            for (Long userId : repo.getUserIds()) {
                stats.put(userId, stats.getOrDefault(userId, 0L) + 1);
            }
        }
        return stats;
    }
}