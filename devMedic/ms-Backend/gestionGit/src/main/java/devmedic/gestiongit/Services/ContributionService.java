package devmedic.gestiongit.Services;

import devmedic.gestiongit.Entities.*;
import devmedic.gestiongit.Repos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContributionService {

    private final CommitRep commitRep;
    private final ContributionRep contributionRep;

    // ✅ Calculer contributions pour un repo donné
    public void calculateContributions(GitRepository repo) {
        List<Commit> commits = commitRep.findByBranch_Repository_Id(repo.getId());

        if (commits.isEmpty()) return;

        // ─── Calcul global (période ALL) ───────────────────────────────
        calculateForPeriod(repo, commits, "ALL");

        // ─── Calcul par année ──────────────────────────────────────────
        Map<String, List<Commit>> byYear = commits.stream()
                .filter(c -> c.getCommittedAt() != null)
                .collect(Collectors.groupingBy(c ->
                        c.getCommittedAt().format(DateTimeFormatter.ofPattern("yyyy"))
                ));

        byYear.forEach((year, yearCommits) ->
                calculateForPeriod(repo, yearCommits, year));

        // ─── Calcul par mois ───────────────────────────────────────────
        Map<String, List<Commit>> byMonth = commits.stream()
                .filter(c -> c.getCommittedAt() != null)
                .collect(Collectors.groupingBy(c ->
                        c.getCommittedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                ));

        byMonth.forEach((month, monthCommits) ->
                calculateForPeriod(repo, monthCommits, month));
    }

    // ─── Calcul par période et par auteur ──────────────────────────────
    private void calculateForPeriod(GitRepository repo, List<Commit> commits, String period) {

        // Grouper par email auteur
        Map<String, List<Commit>> byAuthor = commits.stream()
                .collect(Collectors.groupingBy(Commit::getAuthorEmail));

        for (Map.Entry<String, List<Commit>> entry : byAuthor.entrySet()) {
            String email = entry.getKey();
            List<Commit> authorCommits = entry.getValue();

            // ✅ Upsert — créer ou mettre à jour
            Contribution contribution = contributionRep
                    .findByRepository_IdAndAuthorEmailAndPeriod(repo.getId(), email, period)
                    .orElse(new Contribution());

            // Nom = le plus récent
            String authorName = authorCommits.stream()
                    .filter(c -> c.getAuthorName() != null)
                    .map(Commit::getAuthorName)
                    .findFirst()
                    .orElse("Unknown");

            int totalAdded   = authorCommits.stream().mapToInt(Commit::getLinesAdded).sum();
            int totalDeleted = authorCommits.stream().mapToInt(Commit::getLinesDeleted).sum();

            // ✅ Score = commits * 1.0 + lignes ajoutées * 0.01 - lignes supprimées * 0.005
            double score = authorCommits.size() * 1.0
                    + totalAdded * 0.01
                    - totalDeleted * 0.005;

            contribution.setRepository(repo);
            contribution.setAuthorEmail(email);
            contribution.setAuthorName(authorName);
            contribution.setPeriod(period);
            contribution.setCommitCount(authorCommits.size());
            contribution.setLinesAdded(totalAdded);
            contribution.setLinesDeleted(totalDeleted);
            contribution.setScore(Math.max(score, 0)); // ✅ score jamais négatif

            contributionRep.save(contribution);
        }
    }
}