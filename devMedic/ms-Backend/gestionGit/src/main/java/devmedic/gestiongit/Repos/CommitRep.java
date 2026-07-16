package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.Commit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommitRep extends JpaRepository<Commit, Long> {

    // ✅ Par branch
    Optional<Commit> findByShaAndBranch_Id(String sha, Long branchId);

    List<Commit> findByBranch_Id(Long branchId);

    // ✅ Par repository via branch
    List<Commit> findByBranch_Repository_Id(Long repositoryId);

    List<Commit> findByBranch_Repository_IdAndCommittedAtBetween(
            Long repositoryId, LocalDateTime from, LocalDateTime to);

    List<Commit> findByBranch_IdAndAuthorEmail(Long branchId, String authorEmail);

    @Query("SELECT c FROM Commit c WHERE c.branch.repository.id = :repoId ORDER BY c.committedAt DESC")
    List<Commit> findLatestByRepository(@Param("repoId") Long repoId);

    long countByBranch_Repository_Id(Long repositoryId);
}