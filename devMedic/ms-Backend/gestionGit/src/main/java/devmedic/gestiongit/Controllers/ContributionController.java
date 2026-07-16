package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.Contribution;
import devmedic.gestiongit.Repos.ContributionRep;
import devmedic.gestiongit.Services.ContributionService;
import devmedic.gestiongit.Repos.GitRepositoryRep;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/git/contributions")
@RequiredArgsConstructor
public class ContributionController {

    private final ContributionRep contributionRep;
    private final ContributionService contributionService;
    private final GitRepositoryRep gitRepositoryRep;

    // ✅ Tous les contributeurs d'un repo (triés par score)
    @GetMapping("/repository/{repoId}")
    public ResponseEntity<List<Contribution>> getByRepository(@PathVariable Long repoId) {
        return ResponseEntity.ok(
                contributionRep.findByRepository_IdOrderByScoreDesc(repoId));
    }

    // ✅ Contributeurs par période
    @GetMapping("/repository/{repoId}/period/{period}")
    public ResponseEntity<List<Contribution>> getByPeriod(
            @PathVariable Long repoId,
            @PathVariable String period) {
        return ResponseEntity.ok(
                contributionRep.findByRepository_IdAndPeriod(repoId, period));
    }

    // ✅ Stats globales — bus factor, top contributeur
    @GetMapping("/repository/{repoId}/stats")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable Long repoId) {
        List<Contribution> all = contributionRep
                .findByRepository_IdAndPeriod(repoId, "ALL");

        if (all.isEmpty()) return ResponseEntity.ok(Map.of("total", 0));

        int totalCommits = all.stream().mapToInt(Contribution::getCommitCount).sum();

        // ✅ Bus factor = nombre de devs couvrant 80% des commits
        all.sort((a, b) -> Integer.compare(b.getCommitCount(), a.getCommitCount()));
        int busFactor = 0;
        int cumul = 0;
        for (Contribution c : all) {
            cumul += c.getCommitCount();
            busFactor++;
            if (cumul >= totalCommits * 0.8) break;
        }

        Contribution top = all.get(0);

        return ResponseEntity.ok(Map.of(
                "totalContributors", all.size(),
                "totalCommits", totalCommits,
                "busFactor", busFactor,
                "topContributor", Map.of(
                        "name", top.getAuthorName(),
                        "email", top.getAuthorEmail(),
                        "commits", top.getCommitCount(),
                        "score", top.getScore()
                )
        ));
    }

    // ✅ Recalculer manuellement
    @PostMapping("/repository/{repoId}/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate(@PathVariable Long repoId) {
        gitRepositoryRep.findById(repoId).ifPresent(
                contributionService::calculateContributions);
        return ResponseEntity.ok(Map.of("message", "Contributions recalculées"));
    }
}