package devmedic.gestionuser.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import devmedic.gestionuser.Entities.Role;
import devmedic.gestionuser.Entities.User;
import devmedic.gestionuser.Repos.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserServ {

    private final UserRepo userRepo;

    @Value("${keycloak.admin.url}")
    private String keycloakAdminUrl;

    @Value("${keycloak.admin.username}")
    private String keycloakAdminUsername;

    @Value("${keycloak.admin.password}")
    private String keycloakAdminPassword;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    private static final Set<String> SYSTEM_ROLES = Set.of(
            "DEFAULT-ROLES-DEVMEDIC",
            "OFFLINE_ACCESS",
            "UMA_AUTHORIZATION"
    );

    // ─────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────

    public User create(User user) {
        user.setCreatedAt(LocalDateTime.now());
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.setRoles(new HashSet<>(Set.of(Role.VIEWER)));
        }

        User savedUser = userRepo.save(user);

        try {
            createUserInKeycloak(user.getUsername(), user.getEmail(), user.getRoles());
        } catch (Exception e) {
            System.out.println("⚠️ Erreur création Keycloak: " + e.getMessage());
        }

        return savedUser;
    }

    public User updateUser(Long userId, User updatedUser) {
        User existingUser = getById(userId);

        if (updatedUser.getUsername() != null && !updatedUser.getUsername().isEmpty()) {
            existingUser.setUsername(updatedUser.getUsername());
        }

        if (updatedUser.getEmail() != null && !updatedUser.getEmail().isEmpty()) {
            existingUser.setEmail(updatedUser.getEmail());
        }

        if (updatedUser.getRoles() != null && !updatedUser.getRoles().isEmpty()) {
            existingUser.setRoles(updatedUser.getRoles());
        }

        User savedUser = userRepo.save(existingUser);

        // ⭐ METTRE À JOUR DANS KEYCLOAK (Y COMPRIS LES RÔLES)
        try {
            updateUserInKeycloak(existingUser.getKeycloakId(), existingUser.getUsername(),
                    existingUser.getEmail(), existingUser.getRoles());
        } catch (Exception e) {
            System.out.println("⚠️ Erreur mise à jour Keycloak: " + e.getMessage());
        }

        return savedUser;
    }

    public List<User> getAll() {
        return userRepo.findAll();
    }

    public User getById(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void delete(Long id) {
        userRepo.deleteById(id);
    }

    public void changeEmail(Long userId, String newEmail) {
        User user = getById(userId);
        user.setEmail(newEmail);
        userRepo.save(user);
    }

    public void addRole(Long userId, Role role) {
        User user = getById(userId);
        user.getRoles().add(role);
        userRepo.save(user);
    }

    public boolean isAdmin(Long userId) {
        return getById(userId).getRoles().contains(Role.ADMIN);
    }

    public void recordLogin(Long userId) {
        User user = getById(userId);
        user.setLastLoginAt(LocalDateTime.now());
        userRepo.save(user);
    }

    // ─────────────────────────────────────────────
    // KEYCLOAK MANAGEMENT
    // ─────────────────────────────────────────────

    private String getKeycloakAdminToken() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            HttpHeaders tokenHeaders = new HttpHeaders();
            tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String tokenBody = "grant_type=password&client_id=admin-cli"
                    + "&username=" + keycloakAdminUsername
                    + "&password=" + keycloakAdminPassword;

            ResponseEntity<String> tokenResp = restTemplate.postForEntity(
                    keycloakAdminUrl + "/realms/master/protocol/openid-connect/token",
                    new HttpEntity<>(tokenBody, tokenHeaders),
                    String.class
            );

            return mapper.readTree(tokenResp.getBody()).get("access_token").asText();

        } catch (Exception e) {
            throw new RuntimeException("Erreur obtention token admin: " + e.getMessage());
        }
    }

    public void createUserInKeycloak(String username, String email, Set<Role> roles) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            String adminToken = getKeycloakAdminToken();

            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(adminToken);
            authHeaders.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> keycloakUser = new HashMap<>();
            keycloakUser.put("username", username);
            keycloakUser.put("email", email);
            keycloakUser.put("enabled", true);
            keycloakUser.put("emailVerified", true);
            keycloakUser.put("credentials", List.of(Map.of(
                    "type", "password",
                    "value", "DevMedic@2024",
                    "temporary", true
            )));

            ResponseEntity<String> createResp = restTemplate.postForEntity(
                    keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users",
                    new HttpEntity<>(keycloakUser, authHeaders),
                    String.class
            );

            if (createResp.getStatusCode().value() != 201) {
                throw new RuntimeException("Erreur création user Keycloak");
            }

            String location = createResp.getHeaders().getFirst("Location");
            String keycloakUserId = location.substring(location.lastIndexOf("/") + 1);

            // Assigner les rôles
            assignRolesToKeycloakUser(keycloakUserId, roles, authHeaders);

            System.out.println("✅ User créé dans Keycloak: " + username);

        } catch (Exception e) {
            throw new RuntimeException("Erreur création Keycloak: " + e.getMessage());
        }
    }

    // ⭐ NOUVELLE MÉTHODE : Met à jour l'utilisateur ET ses rôles dans Keycloak
    private void updateUserInKeycloak(String keycloakId, String username, String email, Set<Role> roles) {
        if (keycloakId == null) {
            System.out.println("⚠️ Pas d'ID Keycloak, mise à jour ignorée");
            return;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            String adminToken = getKeycloakAdminToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 1. Mettre à jour les infos de base (email, username)
            Map<String, Object> updates = new HashMap<>();
            if (email != null) updates.put("email", email);
            if (username != null) updates.put("username", username);
            updates.put("enabled", true);

            if (!updates.isEmpty()) {
                restTemplate.exchange(
                        keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users/" + keycloakId,
                        HttpMethod.PUT,
                        new HttpEntity<>(updates, headers),
                        Void.class
                );
                System.out.println("✅ Infos de base mises à jour dans Keycloak");
            }

            // 2. ⭐ METTRE À JOUR LES RÔLES (LE PLUS IMPORTANT)
            updateUserRolesInKeycloak(keycloakId, roles, headers);

        } catch (Exception e) {
            throw new RuntimeException("Erreur mise à jour Keycloak: " + e.getMessage());
        }
    }

    // ⭐ NOUVELLE MÉTHODE : Met à jour UNIQUEMENT les rôles dans Keycloak
    private void updateUserRolesInKeycloak(String keycloakId, Set<Role> newRoles, HttpHeaders headers) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            String rolesUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm
                    + "/users/" + keycloakId + "/role-mappings/realm";

            // 1. Récupérer les rôles actuels
            ResponseEntity<String> currentRolesResp = restTemplate.exchange(
                    rolesUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode currentRoles = mapper.readTree(currentRolesResp.getBody());

            // 2. Supprimer tous les rôles actuels
            if (currentRoles.isArray() && currentRoles.size() > 0) {
                restTemplate.exchange(
                        rolesUrl,
                        HttpMethod.DELETE,
                        new HttpEntity<>(currentRoles, headers),
                        Void.class
                );
                System.out.println("✅ Anciens rôles supprimés");
            }

            // 3. Ajouter les nouveaux rôles
            List<Map<String, String>> rolesToAdd = new ArrayList<>();
            for (Role role : newRoles) {
                ResponseEntity<String> roleResp = restTemplate.exchange(
                        keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/roles/" + role.name(),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
                JsonNode roleNode = mapper.readTree(roleResp.getBody());

                Map<String, String> roleMap = new HashMap<>();
                roleMap.put("id", roleNode.get("id").asText());
                roleMap.put("name", role.name());
                rolesToAdd.add(roleMap);
            }

            if (!rolesToAdd.isEmpty()) {
                restTemplate.exchange(
                        rolesUrl,
                        HttpMethod.POST,
                        new HttpEntity<>(rolesToAdd, headers),
                        Void.class
                );
                System.out.println("✅ Nouveaux rôles assignés: " + newRoles);
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur mise à jour des rôles: " + e.getMessage());
        }
    }

    private void assignRolesToKeycloakUser(String keycloakUserId, Set<Role> roles, HttpHeaders headers) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            List<Map<String, String>> rolesToAdd = new ArrayList<>();
            for (Role role : roles) {
                ResponseEntity<String> roleResp = restTemplate.exchange(
                        keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/roles/" + role.name(),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
                JsonNode roleNode = mapper.readTree(roleResp.getBody());

                Map<String, String> roleMap = new HashMap<>();
                roleMap.put("id", roleNode.get("id").asText());
                roleMap.put("name", role.name());
                rolesToAdd.add(roleMap);
            }

            if (!rolesToAdd.isEmpty()) {
                restTemplate.exchange(
                        keycloakAdminUrl + "/admin/realms/" + keycloakRealm
                                + "/users/" + keycloakUserId + "/role-mappings/realm",
                        HttpMethod.POST,
                        new HttpEntity<>(rolesToAdd, headers),
                        Void.class
                );
            }
        } catch (Exception e) {
            System.out.println("⚠️ Erreur assignation rôles: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    // SYNC KEYCLOAK → POSTGRESQL
    // ─────────────────────────────────────────────

    public Map<String, Object> syncAllFromKeycloak() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            String adminToken = getKeycloakAdminToken();

            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(adminToken);

            ResponseEntity<String> usersResp = restTemplate.exchange(
                    keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users?max=200",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders),
                    String.class
            );

            JsonNode keycloakUsers = mapper.readTree(usersResp.getBody());

            int created = 0;
            int updated = 0;
            int skipped = 0;

            for (JsonNode ku : keycloakUsers) {
                String username = ku.has("username") ? ku.get("username").asText() : null;
                if (username == null) { skipped++; continue; }

                if (username.equals("admin") || username.startsWith("service-account")) {
                    skipped++; continue;
                }

                String email = ku.has("email") && !ku.get("email").isNull()
                        ? ku.get("email").asText()
                        : username + "@devmedic.local";

                String keycloakId = ku.get("id").asText();

                ResponseEntity<String> rolesResp = restTemplate.exchange(
                        keycloakAdminUrl + "/admin/realms/" + keycloakRealm
                                + "/users/" + keycloakId + "/role-mappings/realm",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders),
                        String.class
                );

                JsonNode rolesJson = mapper.readTree(rolesResp.getBody());
                Set<Role> roles = new HashSet<>();

                for (JsonNode roleNode : rolesJson) {
                    String roleName = roleNode.get("name").asText().toUpperCase().trim();
                    if (SYSTEM_ROLES.contains(roleName)) continue;
                    try {
                        roles.add(Role.valueOf(roleName));
                    } catch (Exception ignored) {}
                }

                if (roles.isEmpty()) roles.add(Role.VIEWER);

                Optional<User> existingUser = userRepo.findByUsername(username);

                if (existingUser.isPresent()) {
                    User user = existingUser.get();
                    user.setRoles(roles);
                    user.setEmail(email);
                    user.setKeycloakId(keycloakId);
                    userRepo.save(user);
                    updated++;
                } else {
                    User user = new User();
                    user.setUsername(username);
                    user.setEmail(email);
                    user.setCreatedAt(LocalDateTime.now());
                    user.setRoles(roles);
                    user.setKeycloakId(keycloakId);
                    userRepo.save(user);
                    created++;
                }
            }

            return Map.of(
                    "message", "Synchronisation terminée",
                    "created", created,
                    "updated", updated,
                    "skipped", skipped
            );

        } catch (Exception e) {
            throw new RuntimeException("Erreur sync Keycloak: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    // CHANGE PASSWORD
    // ─────────────────────────────────────────────

    public void changePasswordInKeycloak(String keycloakId, String newPassword) {
        if (keycloakId == null) {
            throw new RuntimeException("ID Keycloak non trouvé");
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper mapper = new ObjectMapper();

            String adminToken = getKeycloakAdminToken();

            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(adminToken);
            authHeaders.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> passwordBody = Map.of(
                    "type", "password",
                    "value", newPassword,
                    "temporary", false
            );

            restTemplate.exchange(
                    keycloakAdminUrl + "/admin/realms/" + keycloakRealm
                            + "/users/" + keycloakId + "/reset-password",
                    HttpMethod.PUT,
                    new HttpEntity<>(passwordBody, authHeaders),
                    Void.class
            );

        } catch (Exception e) {
            throw new RuntimeException("Erreur changement mot de passe: " + e.getMessage());
        }
    }

    public User getUserByKeycloakId(String keycloakId) {
        return userRepo.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RuntimeException("User not found with Keycloak ID: " + keycloakId));
    }

    public void saveTokens(String keycloakId, String githubToken, String gitlabToken) {
        User user = userRepo.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RuntimeException("User non trouvé"));
        if (githubToken != null) user.setGithubToken(githubToken);
        if (gitlabToken != null) user.setGitlabToken(gitlabToken);
        userRepo.save(user);
    }
}