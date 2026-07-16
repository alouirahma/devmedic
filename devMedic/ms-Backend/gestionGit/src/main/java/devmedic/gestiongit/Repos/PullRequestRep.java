// PullRequestRep.java
package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.PullRequest;
import devmedic.gestiongit.Entities.PullRequestState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PullRequestRep extends JpaRepository<PullRequest, Long> {
    List<PullRequest> findByRepository_Id(Long repositoryId);
    List<PullRequest> findByRepository_IdAndState(Long repositoryId, PullRequestState state);
    Optional<PullRequest> findByNumberAndRepository_Id(int number, Long repositoryId);
}