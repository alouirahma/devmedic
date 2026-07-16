// QualityMetricRep.java
package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.QualityMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface QualityMetricRep extends JpaRepository<QualityMetric, Long> {
    Optional<QualityMetric> findByCommit_Id(Long commitId);
    Optional<QualityMetric> findByRepository_Id(Long repositoryId);
    @Query("SELECT q FROM QualityMetric q WHERE q.commit.branch.repository.id = :repoId ORDER BY q.calculatedAt DESC")
    Optional<QualityMetric> findLatestByRepositoryId(@Param("repoId") Long repoId);
}