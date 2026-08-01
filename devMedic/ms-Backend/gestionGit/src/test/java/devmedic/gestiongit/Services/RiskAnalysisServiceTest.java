package devmedic.gestiongit.Services;

import devmedic.gestiongit.Entities.Commit;
import devmedic.gestiongit.Repos.*;
import devmedic.gestiongit.Services.RiskAnalysisService;
import devmedic.gestiongit.Services.UserClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RiskAnalysisServiceTest {

    @Mock private CommitRep commitRep;
    @Mock private PushRep pushRep;
    @Mock private PullRequestRep pullRequestRep;
    @Mock private RiskScoreRep riskScoreRep;
    @Mock private UserClientService userClientService;

    @InjectMocks
    private RiskAnalysisService riskAnalysisService;

    @Test
    void busFactorEstUnQuandUnSeulAuteurFaitTousLesCommits() {
        List<Commit> commits = List.of(
                commitDe("alice"), commitDe("alice"), commitDe("alice"), commitDe("alice")
        );

        int busFactor = riskAnalysisService.computeBusFactor(commits);

        assertThat(busFactor).isEqualTo(1);
    }

    @Test
    void busFactorAugmenteQuandLeTravailEstReparti() {
        List<Commit> commits = List.of(
                commitDe("alice"), commitDe("bob"), commitDe("charlie"), commitDe("dave"),
                commitDe("alice"), commitDe("bob")
        );

        int busFactor = riskAnalysisService.computeBusFactor(commits);

        assertThat(busFactor).isGreaterThan(1);
    }

    @Test
    void busFactorDe1EstConsidereCritique() {
        double score = riskAnalysisService.normalizeBusFactor(1);

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void busFactorDe4OuPlusEstConsidereExcellent() {
        assertThat(riskAnalysisService.normalizeBusFactor(4)).isEqualTo(1.0);
        assertThat(riskAnalysisService.normalizeBusFactor(6)).isEqualTo(1.0);
    }

    private Commit commitDe(String auteur) {
        Commit commit = new Commit();
        commit.setAuthorName(auteur);
        commit.setAuthorEmail(auteur + "@test.com");
        commit.setLinesAdded(10);
        commit.setLinesDeleted(2);
        commit.setCommittedAt(LocalDateTime.now());
        commit.setSha("abc1234567890");
        commit.setMessage("Test commit");
        return commit;
    }
}