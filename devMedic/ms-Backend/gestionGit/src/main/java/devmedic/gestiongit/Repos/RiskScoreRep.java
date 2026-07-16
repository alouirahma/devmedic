// RiskScoreRep.java
package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RiskScoreRep extends JpaRepository<RiskScore, Long> {
    Optional<RiskScore> findByCommit_Id(Long commitId);
    Optional<RiskScore> findByRepository_Id(Long repositoryId);
    Optional<RiskScore> findFirstByCommit_Branch_Repository_IdOrderByCalculatedAtDesc(Long repoId);
    //  Commit → Branch → Repository (pas Commit → Repository directement)
    @Query("SELECT r FROM RiskScore r WHERE r.commit.branch.repository.id = :repoId ORDER BY r.calculatedAt DESC")
    Optional<RiskScore> findLatestByRepositoryId(@Param("repoId") Long repoId);
}