package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.*;
import devmedic.gestiongit.Repos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/git/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final GitRepositoryRep    repoRep;
    private final CommitRep           commitRep;
    private final BranchRep           branchRep;
    private final PullRequestRep      pullRequestRep;
    private final ContributionRep     contributionRep;
    private final PushRep             pushRep;

    // ─── Dashboard global (tous repos de l'utilisateur) ───────────────
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        List<GitRepository> repos = repoRep.findAll();

        long totalCommits = repos.stream()
                .mapToLong(r -> commitRep.countByBranch_Repository_Id(r.getId()))
                .sum();

        long totalBranches = branchRep.count();
        long totalPRs      = pullRequestRep.count();
        long totalRepos    = repos.size();

        // Commits par provider
        Map<String, Long> reposByProvider = repos.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getProvider().toString(), Collectors.counting()));

        return ResponseEntity.ok(Map.of(
                "totalRepos",    totalRepos,
                "totalCommits",  totalCommits,
                "totalBranches", totalBranches,
                "totalPRs",      totalPRs,
                "reposByProvider", reposByProvider
        ));
    }

    // ─── Dashboard par repo ───────────────────────────────────────────
    @GetMapping("/{repoId}")
    public ResponseEntity<Map<String, Object>> getByRepo(@PathVariable Long repoId) {

        // ── Commits ───────────────────────────────────────────────────
        List<Commit> commits = commitRep.findByBranch_Repository_Id(repoId);

        long totalAdded   = commits.stream().mapToLong(Commit::getLinesAdded).sum();
        long totalDeleted = commits.stream().mapToLong(Commit::getLinesDeleted).sum();

        // Commits groupés par mois (yyyy-MM)
        Map<String, Long> commitsByMonth = commits.stream()
                .filter(c -> c.getCommittedAt() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getCommittedAt().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()
                ));

        // Top auteurs
        Map<String, Long> commitsByAuthor = commits.stream()
                .collect(Collectors.groupingBy(Commit::getAuthorName, Collectors.counting()));

        List<Map<String, Object>> topAuthors = commitsByAuthor.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "commits", e.getValue()))
                .collect(Collectors.toList());

        // ── Branches ──────────────────────────────────────────────────
        List<Branch> branches     = branchRep.findByRepository_Id(repoId);
        long activeBranches = branches.stream()
                .filter(b -> b.getLastCommitTime() != null &&
                        b.getLastCommitTime().isAfter(
                                java.time.LocalDateTime.now().minusDays(30)))
                .count();
        long staleBranches = branches.size() - activeBranches;

        // ── Pull Requests ─────────────────────────────────────────────
        List<PullRequest> prs = pullRequestRep.findByRepository_Id(repoId);
        long open   = prs.stream().filter(p -> p.getState() == PullRequestState.OPEN).count();
        long merged = prs.stream().filter(p -> p.getState() == PullRequestState.MERGED).count();
        long closed = prs.stream().filter(p -> p.getState() == PullRequestState.CLOSED).count();
        double mergeRate = prs.isEmpty() ? 0 :
                Math.round((merged * 100.0 / prs.size()) * 10.0) / 10.0;

        // ── Contributions ─────────────────────────────────────────────
        List<Contribution> contributions = contributionRep
                .findByRepository_IdAndPeriod(repoId, "ALL");

        contributions.sort((a, b) -> Integer.compare(b.getCommitCount(), a.getCommitCount()));

        // Bus factor
        int totalContribCommits = contributions.stream().mapToInt(Contribution::getCommitCount).sum();
        int busFactor = 0, cumul = 0;
        for (Contribution c : contributions) {
            cumul += c.getCommitCount();
            busFactor++;
            if (cumul >= totalContribCommits * 0.8) break;
        }

        List<Map<String, Object>> topContributors = contributions.stream()
                .limit(5)
                .map(c -> Map.<String, Object>of(
                        "name",    c.getAuthorName(),
                        "email",   c.getAuthorEmail(),
                        "commits", c.getCommitCount(),
                        "added",   c.getLinesAdded(),
                        "deleted", c.getLinesDeleted(),
                        "score",   c.getScore()
                ))
                .collect(Collectors.toList());

        // ── Pushes ────────────────────────────────────────────────────
        List<Push> pushes = pushRep.findByBranch_Repository_Id(repoId);
        long forcePushCount = pushes.stream().filter(Push::isForcePush).count();

        // ── Réponse agrégée ───────────────────────────────────────────
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("commits", Map.of(
                "total",       commits.size(),
                "linesAdded",  totalAdded,
                "linesDeleted", totalDeleted,
                "byMonth",     commitsByMonth,
                "topAuthors",  topAuthors
        ));

        result.put("branches", Map.of(
                "total",   branches.size(),
                "active",  activeBranches,
                "stale",   staleBranches
        ));

        result.put("pullRequests", Map.of(
                "total",     prs.size(),
                "open",      open,
                "merged",    merged,
                "closed",    closed,
                "mergeRate", mergeRate
        ));

        result.put("contributions", Map.of(
                "total",           contributions.size(),
                "busFactor",       busFactor,
                "topContributors", topContributors
        ));

        result.put("pushes", Map.of(
                "total",          pushes.size(),
                "forcePushCount", forcePushCount
        ));

        return ResponseEntity.ok(result);
    }
}