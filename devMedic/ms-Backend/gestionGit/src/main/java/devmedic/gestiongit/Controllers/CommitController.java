package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.Commit;
import devmedic.gestiongit.Repos.CommitRep;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git/commits")
@RequiredArgsConstructor
public class CommitController {

    private final CommitRep commitRep;

    @GetMapping("/repository/{repoId}")
    public ResponseEntity<List<Commit>> getByRepository(@PathVariable Long repoId) {
        return ResponseEntity.ok(commitRep.findByBranch_Repository_Id(repoId)); // ✅
    }

    @GetMapping("/repository/{repoId}/period")
    public ResponseEntity<List<Commit>> getByPeriod(
            @PathVariable Long repoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(
                commitRep.findByBranch_Repository_IdAndCommittedAtBetween(repoId, from, to)); // ✅
    }

    // Stats commits d'un repo
    @GetMapping("/repository/{repoId}/stats")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable Long repoId) {
        List<Commit> commits = commitRep.findByBranch_Repository_Id(repoId); // ✅
        Map<String, Long> byAuthor = commits.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Commit::getAuthorName, java.util.stream.Collectors.counting()));

        return ResponseEntity.ok(Map.of(
                "total", commits.size(),
                "linesAdded", commits.stream().mapToInt(Commit::getLinesAdded).sum(),
                "linesDeleted", commits.stream().mapToInt(Commit::getLinesDeleted).sum(),
                "byAuthor", byAuthor
        ));
    }
}