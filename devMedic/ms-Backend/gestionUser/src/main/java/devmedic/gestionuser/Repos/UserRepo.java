package devmedic.gestionuser.Repos;

import devmedic.gestionuser.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    // Dans UserRepo.java
    Optional<User> findByKeycloakId(String keycloakId);  // ou findByIdkeycloak selon votre nommage
}
