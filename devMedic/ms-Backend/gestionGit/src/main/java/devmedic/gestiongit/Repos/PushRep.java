// PushRep.java
package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.Push;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PushRep extends JpaRepository<Push, Long> {
    List<Push> findByBranch_Id(Long branchId);
    List<Push> findByBranch_Repository_Id(Long repositoryId);
}