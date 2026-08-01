package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.PredictedRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PredictedRiskRep extends JpaRepository<PredictedRisk, Long> {
    Optional<PredictedRisk> findByRepository_Id(Long repositoryId);
}