package devmedic.gestionuser.Repos;

import devmedic.gestionuser.Entities.UserHR;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserHRepo extends JpaRepository<UserHR ,Long > {
    Optional<UserHR> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
