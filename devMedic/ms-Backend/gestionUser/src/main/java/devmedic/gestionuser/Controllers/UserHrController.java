package devmedic.gestionuser.Controllers;

import devmedic.gestionuser.Entities.SeniorityLevel;
import devmedic.gestionuser.Entities.User;
import devmedic.gestionuser.Entities.UserHR;
import devmedic.gestionuser.Services.UserHrServ;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/user-rh")
@RequiredArgsConstructor
public class UserHrController {

    private final UserHrServ userHrServ;

    // DTO pour la création (sans la relation User)
    public static class CreateUserHRRequest {
        public String seniorityLevel;
        public int yearsOfExperience;
        public String primarySkill;
    }

    // ─────────────────────────────────────────────
    // 🔐 ADMIN SEULEMENT
    // ─────────────────────────────────────────────

    @PostMapping("/{userId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> create(
            @PathVariable Long userId,
            @RequestBody CreateUserHRRequest request) {
        try {
            System.out.println("📥 Création fiche RH pour user: " + userId);
            System.out.println("   Seniority: " + request.seniorityLevel);
            System.out.println("   Expérience: " + request.yearsOfExperience + " ans");
            System.out.println("   Compétence: " + request.primarySkill);

            // Créer l'entité UserHR à partir du DTO
            UserHR userHR = new UserHR();
            userHR.setSeniorityLevel(SeniorityLevel.valueOf(request.seniorityLevel));
            userHR.setYearsOfExperience(request.yearsOfExperience);
            userHR.setPrimarySkill(request.primarySkill);

            UserHR created = userHrServ.create(userId, userHR);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            System.err.println(" Erreur création RH: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{userId}/seniority")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateSeniority(
            @PathVariable Long userId,
            @RequestBody String newLevel) {
        try {
            // Nettoyer la chaîne (enlève les guillemets si présents)
            String cleanLevel = newLevel.replace("\"", "");
            System.out.println("📥 Mise à jour séniorité user " + userId + " -> " + cleanLevel);

            userHrServ.updateSeniority(userId, SeniorityLevel.valueOf(cleanLevel));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────
    // 👥 ADMIN + TEAM LEAD
    // ─────────────────────────────────────────────

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_TEAM_LEAD')")
    public ResponseEntity<?> get(@PathVariable Long userId) {
        Optional<UserHR> userHR = userHrServ.getByUserOptional(userId);
        if (userHR.isPresent()) {
            return ResponseEntity.ok(userHR.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Fiche RH non trouvée pour l'utilisateur " + userId));
        }
    }

    @GetMapping("/{userId}/is-senior")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_TEAM_LEAD')")
    public ResponseEntity<Boolean> isSeniorOrAbove(@PathVariable Long userId) {
        return ResponseEntity.ok(userHrServ.isSeniorOrAbove(userId));
    }
}