package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.Contribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContributionRep extends JpaRepository<Contribution, Long> {

    List<Contribution> findByRepository_Id(Long repositoryId);

    List<Contribution> findByRepository_IdOrderByScoreDesc(Long repositoryId);

    // ✅ Cherche par email au lieu de userId
    Optional<Contribution> findByRepository_IdAndAuthorEmailAndPeriod(
            Long repositoryId, String authorEmail, String period);

    List<Contribution> findByRepository_IdAndPeriod(Long repositoryId, String period);
}