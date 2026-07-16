package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.Branch;
import devmedic.gestiongit.Entities.GitRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRep extends JpaRepository<Branch, Long> {

    Optional<Branch> findByRepositoryAndName(GitRepository repository, String name);
    List<Branch> findByRepository(GitRepository repository);
    List<Branch> findByRepositoryAndIsDefaultTrue(GitRepository repository);
    Optional<Branch> findByRepository_IdAndName(Long repositoryId, String name);
    List<Branch> findByRepository_Id(Long repositoryId);
    Optional<Branch> findByRepository_IdAndIsDefaultTrue(Long repositoryId);
    List<Branch> findByLastCommitTimeBefore(LocalDateTime threshold);
    List<Branch> findByLastCommitTimeAfter(LocalDateTime threshold);
    long countByProtectedBranchTrue();
    Optional<Branch> findByRepository_IdAndIsDefault(Long repositoryId, boolean isDefault);

    // ✅ Remplace findByRepository_UserId — userIds est une collection
    @Query("SELECT b FROM Branch b WHERE :userId MEMBER OF b.repository.userIds")
    List<Branch> findByRepositoryUserIdsContaining(@Param("userId") Long userId);

    @Query(value = "SELECT b.repository_id, COUNT(*) as branch_count FROM git_branch b GROUP BY b.repository_id ORDER BY branch_count DESC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> countBranchesByRepositoryGrouped(@Param("limit") int limit);
}