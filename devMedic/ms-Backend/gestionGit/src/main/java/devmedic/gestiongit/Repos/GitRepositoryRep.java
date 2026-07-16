package devmedic.gestiongit.Repos;

import devmedic.gestiongit.Entities.GitRepository;
import devmedic.gestiongit.Entities.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GitRepositoryRep extends JpaRepository<GitRepository, Long> {

    Optional<GitRepository> findByRemoteIdAndProvider(String remoteId, ProviderType provider);

    // ✅ Cherche tous les repos où userId est dans la liste userIds
    List<GitRepository> findByUserIdsContaining(Long userId);

    List<GitRepository> findByProvider(ProviderType provider);

    // ✅ Repos d'un user filtré par provider
    List<GitRepository> findByUserIdsContainingAndProvider(Long userId, ProviderType provider);

    long countByProvider(ProviderType provider);

    long countByIsPrivateFalse();

    long countByIsPrivateTrue();

    List<GitRepository> findTop10ByOrderByLastAnalyzedAtDesc();
}