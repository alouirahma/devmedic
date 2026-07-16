package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.GitRepository;
import devmedic.gestiongit.Entities.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagRep extends JpaRepository<Tag, Long> {

    List<Tag> findByRepository(GitRepository repository);

    List<Tag> findByRepository_IdOrderByTaggedAtDesc(Long repositoryId);

    List<Tag> findByRepository_Id(Long repositoryId);

    List<Tag> findTop10ByOrderByTaggedAtDesc();

    // ✅ Remplace findByRepository_UserId
    @Query("SELECT t FROM Tag t WHERE :userId MEMBER OF t.repository.userIds")
    List<Tag> findByRepositoryUserIdsContaining(@Param("userId") Long userId);
}