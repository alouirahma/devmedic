package devmedic.gestionuser.Controllers;

import devmedic.gestionuser.Entities.Role;
import devmedic.gestionuser.Entities.User;
import devmedic.gestionuser.Services.KeycloakBrokerTokenService;
import devmedic.gestionuser.Services.UserServ;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserServ userServ;
    private final KeycloakBrokerTokenService brokerTokenService;

    // ─────────────────────────────────────────────
    // 👤 PROFIL UTILISATEUR CONNECTÉ
    // ─────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Non authentifié"));
        }

        String email = jwt.getClaimAsString("email");
        String username = jwt.getClaimAsString("preferred_username");

        // Extraction sécurisée des rôles depuis Keycloak
        List<String> roles = List.of();
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            roles = (List<String>) realmAccess.get("roles");
        }

        return ResponseEntity.ok(Map.of(
                "id", jwt.getClaimAsString("sub"),           // ID unique de l'utilisateur
                "username", username != null ? username : "",
                "email", email != null ? email : "",
                "roles", roles,
                "authenticated", true
        ));
    }

    // ─────────────────────────────────────────────
    // 🔐 ADMIN SEULEMENT
    // ─────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<User> create(@RequestBody User user) {
        return ResponseEntity.ok(userServ.create(user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userServ.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/email")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> changeEmail(
            @PathVariable Long id,
            @RequestBody String newEmail) {

        userServ.changeEmail(id, newEmail);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> addRole(
            @PathVariable Long id,
            @RequestBody Role role) {

        userServ.addRole(id, role);
        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────
    // 👥 ADMIN + TEAM LEADER
    // ─────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_TEAM_LEAD')")
    public ResponseEntity<List<User>> getAll() {
        return ResponseEntity.ok(userServ.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_TEAM_LEAD')")
    public ResponseEntity<User> get(@PathVariable Long id) {
        return ResponseEntity.ok(userServ.getById(id));
    }

    @PostMapping("/sync-keycloak")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> syncFromKeycloak() {
        return ResponseEntity.ok(userServ.syncAllFromKeycloak());
    }

    @PutMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {

        String keycloakId = jwt.getClaimAsString("sub");  // C'est un String
        String newPassword = body.get("newPassword");

        userServ.changePasswordInKeycloak(keycloakId, newPassword);  // ← PAS de Long.valueOf()
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<User> updateUser(
            @PathVariable Long id,
            @RequestBody User user) {

        User updatedUser = userServ.updateUser(id, user);
        return ResponseEntity.ok(updatedUser);
    }



    // Récupérer un user par son keycloakId
    @GetMapping("/by-keycloak/{keycloakId}")
    public ResponseEntity<User> getByKeycloakId(@PathVariable String keycloakId) {
        return ResponseEntity.ok(userServ.getUserByKeycloakId(keycloakId));
    }
    @GetMapping("/me/github-token")
    public ResponseEntity<Map<String, String>> fetchAndSaveGithubToken(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Authorization") String authHeader) {

        String keycloakAccessToken = authHeader.replace("Bearer ", "").trim();
        String keycloakId = jwt.getClaimAsString("sub");

        String githubToken = brokerTokenService.getBrokeredToken(keycloakAccessToken, "github");

        if (githubToken == null || githubToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Token GitHub non trouvé. Vérifiez Store Tokens et le rôle broker/read-token."));
        }

        userServ.saveTokens(keycloakId, githubToken, null);
        System.out.println("✅ Token GitHub sauvegardé pour " + keycloakId);

        return ResponseEntity.ok(Map.of("status", "token GitHub sauvegardé"));
    }
    @PostMapping("/me/github-token")
    public ResponseEntity<Map<String, String>> saveGithubToken(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {

        String keycloakId = jwt.getClaimAsString("sub");
        String githubToken = body.get("githubToken");

        if (githubToken == null || githubToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "githubToken est requis"));
        }

        userServ.saveTokens(keycloakId, githubToken, null);
        System.out.println("✅ Token GitHub sauvegardé (POST) pour " + keycloakId);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Token GitHub sauvegardé avec succès"
        ));
    }
    @PostMapping("/me/save-tokens")
    public ResponseEntity<Void> saveTokens(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getClaimAsString("sub");
        String githubToken = jwt.getClaimAsString("githubToken");
        String gitlabToken = jwt.getClaimAsString("gitlabToken");
        userServ.saveTokens(keycloakId, githubToken, gitlabToken);
        return ResponseEntity.ok().build();
    }
}