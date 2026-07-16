package devmedic.gestiongit.Services;

import devmedic.gestiongit.Entities.*;
import devmedic.gestiongit.Repos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service d'évaluation des risques du projet — cahier des charges §3.8.
 *
 * Périmètre "Niveau 1" : calcul basé sur les données déjà collectées par
 * GitHubService/GitLabService (commits, pushes, PR, contributeurs), sans appel
 * API supplémentaire par commit. Les "hotspots" sont définis au niveau commit
 * (commits anormalement volumineux), pas encore au niveau fichier individuel —
 * cette granularité plus fine est une amélioration possible ultérieure
 * (nécessiterait l'endpoint détail GitHub /commits/{sha} avec son coût en
 * appels API supplémentaires).
 */
@Service
@RequiredArgsConstructor
public class RiskAnalysisService {

    private final CommitRep commitRep;
    private final PushRep pushRep;
    private final PullRequestRep pullRequestRep;
    private final RiskScoreRep riskScoreRep;
    private final UserClientService userClientService;

    private static final double OUTLIER_STDDEV_MULTIPLIER = 2.0; // seuil statistique hotspot
    private static final long MINUTES_PER_HOTSPOT = 30L;          // estimation dette technique

    public RiskAnalysisResult analyze(Long repoId, String jwtToken) {
        List<Commit> commits = commitRep.findByBranch_Repository_Id(repoId);

        if (commits.isEmpty()) {
            return RiskAnalysisResult.empty();
        }

        // ── 1. Détection des hotspots (commits anormalement volumineux) ──────
        List<Integer> changeSizes = commits.stream()
                .map(c -> c.getLinesAdded() + c.getLinesDeleted())
                .collect(Collectors.toList());

        double mean = changeSizes.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = changeSizes.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double threshold = mean + OUTLIER_STDDEV_MULTIPLIER * stdDev;

        List<Commit> hotspotCommits = commits.stream()
                .filter(c -> (c.getLinesAdded() + c.getLinesDeleted()) > threshold && threshold > 0)
                .collect(Collectors.toList());

        int hotspotCount = hotspotCommits.size();

        // ── 2. Facteurs de stabilité ──────────────────────────────────────────
        double hotspotRatio = (double) hotspotCount / commits.size();

        List<Push> pushes = pushRep.findByBranch_Repository_Id(repoId);
        long totalPushes = pushes.size();
        long forcePushes = pushes.stream().filter(Push::isForcePush).count();
        double forcePushRatio = totalPushes > 0 ? (double) forcePushes / totalPushes : 0;

        List<PullRequest> prs = pullRequestRep.findByRepository_Id(repoId);
        long mergedPrs = prs.stream().filter(p -> p.getState() == PullRequestState.MERGED).count();
        double mergeRate = !prs.isEmpty() ? (double) mergedPrs / prs.size() : 1.0; // pas de PR = neutre

        int busFactor = computeBusFactor(commits);
        double busFactorScore = normalizeBusFactor(busFactor);

        // ── 3. Score de stabilité global (0-100, 100 = très stable) ──────────
        double stabilityScore =
                (1 - hotspotRatio) * 30
                        + busFactorScore * 25
                        + (1 - forcePushRatio) * 25
                        + mergeRate * 20;

        stabilityScore = Math.max(0, Math.min(100, stabilityScore));

        // ── 4. Dette technique estimée ────────────────────────────────────────
        long technicalDebtMinutes = hotspotCount * MINUTES_PER_HOTSPOT;

        // ── 5. Persistance — attachée au commit le plus récent ───────────────
        // ── 5. Persistance — un seul score par repository ────────────────────
        Commit latestCommit = commits.stream()
                .max(Comparator.comparing(Commit::getCommittedAt))
                .orElse(commits.get(0));

        RiskScore riskScore = riskScoreRep.findByRepository_Id(repoId)
                .orElse(new RiskScore());

        riskScore.setStabilityScore(Math.round(stabilityScore * 10) / 10.0);
        riskScore.setHotspotCount(hotspotCount);
        riskScore.setTechnicalDebtMinutes(technicalDebtMinutes);
        riskScore.setCalculatedAt(LocalDateTime.now());
        riskScore.setCommit(latestCommit);                                    // référence informative
        riskScore.setRepository(latestCommit.getBranch().getRepository());   // ✅ clé logique
        riskScoreRep.save(riskScore);

        // ── 6. Modules instables par séniorité (croisement contributeurs) ────
        List<SeniorityRiskBreakdown> bySeniority = computeRiskBySeniority(hotspotCommits, jwtToken);

        return new RiskAnalysisResult(
                riskScore.getStabilityScore(),
                hotspotCount,
                technicalDebtMinutes,
                getRiskLevel(stabilityScore),
                bySeniority,
                hotspotCommits.stream()
                        .sorted(Comparator.comparing(Commit::getCommittedAt).reversed())
                        .limit(10)
                        .map(this::toHotspotSummary)
                        .collect(Collectors.toList())
        );
    }

    /** Récupère le dernier score calculé, sans recalculer (pour affichage rapide). */
    public Optional<RiskScore> getLatest(Long repoId) {
        return riskScoreRep.findLatestByRepositoryId(repoId);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private int computeBusFactor(List<Commit> commits) {
        Map<String, Long> byAuthor = commits.stream()
                .collect(Collectors.groupingBy(Commit::getAuthorName, Collectors.counting()));

        long total = commits.size();
        List<Long> sortedDesc = byAuthor.values().stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        long cumulative = 0;
        int count = 0;
        for (Long c : sortedDesc) {
            cumulative += c;
            count++;
            if (cumulative >= total * 0.8) break; // 80% du code couvert
        }
        return count;
    }

    private double normalizeBusFactor(int busFactor) {
        // bus factor 1 = critique (score 0) ; 4+ = très bon (score 1)
        if (busFactor <= 1) return 0.0;
        if (busFactor >= 4) return 1.0;
        return (busFactor - 1) / 3.0;
    }

    private String getRiskLevel(double stabilityScore) {
        if (stabilityScore >= 70) return "STABLE";
        if (stabilityScore >= 40) return "MODERATE";
        return "CRITICAL";
    }

    /**
     * Croise les auteurs de commits hotspots avec leur séniorité (table Users),
     * pour répondre à "modules instables par séniorité" (§3.8).
     * Si le service utilisateur est indisponible, retourne une liste basée sur
     * "Non défini" plutôt que d'échouer.
     */
    private List<SeniorityRiskBreakdown> computeRiskBySeniority(List<Commit> hotspotCommits, String jwtToken) {
        List<UserClientService.UserResponse> users =
                jwtToken != null ? userClientService.getAllUsers(jwtToken) : List.of();

        Map<String, String> emailToSeniority = users.stream()
                .filter(u -> u.getEmail() != null)
                .collect(Collectors.toMap(
                        u -> u.getEmail().toLowerCase(),
                        u -> u.getSeniority() != null ? u.getSeniority() : "NON_DEFINI",
                        (a, b) -> a
                ));

        Map<String, Long> hotspotsBySeniority = hotspotCommits.stream()
                .collect(Collectors.groupingBy(
                        c -> emailToSeniority.getOrDefault(
                                c.getAuthorEmail() != null ? c.getAuthorEmail().toLowerCase() : "",
                                "NON_DEFINI"),
                        Collectors.counting()
                ));

        return hotspotsBySeniority.entrySet().stream()
                .map(e -> new SeniorityRiskBreakdown(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(SeniorityRiskBreakdown::getHotspotCount).reversed())
                .collect(Collectors.toList());
    }

    private HotspotSummary toHotspotSummary(Commit c) {
        return new HotspotSummary(
                c.getSha().substring(0, Math.min(7, c.getSha().length())),
                c.getMessage(),
                c.getAuthorName(),
                c.getLinesAdded() + c.getLinesDeleted(),
                c.getCommittedAt()
        );
    }

    // ────────────────────────────────────────────────────────────────────────
    // DTOs internes
    // ────────────────────────────────────────────────────────────────────────

    public record RiskAnalysisResult(
            double stabilityScore,
            int hotspotCount,
            long technicalDebtMinutes,
            String riskLevel,
            List<SeniorityRiskBreakdown> bySeniority,
            List<HotspotSummary> topHotspots
    ) {
        public static RiskAnalysisResult empty() {
            return new RiskAnalysisResult(100.0, 0, 0L, "STABLE", List.of(), List.of());
        }
    }

    public static class SeniorityRiskBreakdown {
        private final String seniority;
        private final long hotspotCount;

        public SeniorityRiskBreakdown(String seniority, long hotspotCount) {
            this.seniority = seniority;
            this.hotspotCount = hotspotCount;
        }
        public String getSeniority() { return seniority; }
        public long getHotspotCount() { return hotspotCount; }
    }

    public record HotspotSummary(
            String shortSha,
            String message,
            String authorName,
            int totalChanges,
            LocalDateTime committedAt
    ) {}
}