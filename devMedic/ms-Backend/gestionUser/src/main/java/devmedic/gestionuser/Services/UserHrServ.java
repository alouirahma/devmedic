package devmedic.gestionuser.Services;

import devmedic.gestionuser.Entities.SeniorityLevel;
import devmedic.gestionuser.Entities.User;
import devmedic.gestionuser.Entities.UserHR;
import devmedic.gestionuser.Repos.UserHRepo;
import devmedic.gestionuser.Repos.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserHrServ {

    private final UserHRepo userHRepo;
    private final UserRepo userRepo;

    public UserHR create(Long userId, UserHR userHR) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Vérifier si une fiche existe déjà
        if (userHRepo.findByUserId(userId).isPresent()) {
            throw new RuntimeException("Une fiche RH existe déjà pour cet utilisateur");
        }

        userHR.setUser(user);
        return userHRepo.save(userHR);
    }

    public Optional<UserHR> getByUserOptional(Long userId) {
        return userHRepo.findByUserId(userId);
    }

    public UserHR getByUser(Long userId) {
        return userHRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("UserHR not found for user id: " + userId));
    }

    public boolean isSeniorOrAbove(Long userId) {
        Optional<UserHR> userHROpt = userHRepo.findByUserId(userId);
        if (userHROpt.isEmpty()) {
            return false;
        }
        UserHR userHR = userHROpt.get();
        return userHR.getSeniorityLevel() == SeniorityLevel.SENIOR
                || userHR.getSeniorityLevel() == SeniorityLevel.LEAD
                || userHR.getSeniorityLevel() == SeniorityLevel.STAFF;
    }

    public void updateSeniority(Long userId, SeniorityLevel newLevel) {
        UserHR userHR = getByUser(userId);
        userHR.setSeniorityLevel(newLevel);
        userHRepo.save(userHR);
    }
}