package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.PullRequest;
import devmedic.gestiongit.Entities.PullRequestState;
import devmedic.gestiongit.Repos.PullRequestRep;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git/pull-requests")
@RequiredArgsConstructor
public class PullRequestController {

    private final PullRequestRep pullRequestRep;

    @GetMapping("/repository/{repoId}")
    public ResponseEntity<List<PullRequest>> getByRepository(@PathVariable Long repoId) {
        return ResponseEntity.ok(pullRequestRep.findByRepository_Id(repoId));
    }

    @GetMapping("/repository/{repoId}/stats")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable Long repoId) {
        List<PullRequest> all = pullRequestRep.findByRepository_Id(repoId);

        long open   = all.stream().filter(pr -> pr.getState() == PullRequestState.OPEN).count();
        long merged = all.stream().filter(pr -> pr.getState() == PullRequestState.MERGED).count();
        long closed = all.stream().filter(pr -> pr.getState() == PullRequestState.CLOSED).count();
        double mergeRate = all.isEmpty() ? 0 :
                Math.round((merged * 100.0 / all.size()) * 10.0) / 10.0;

        return ResponseEntity.ok(Map.of(
                "total",     all.size(),
                "open",      open,
                "merged",    merged,
                "closed",    closed,
                "mergeRate", mergeRate
        ));
    }
}