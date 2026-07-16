package devmedic.gestiongit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

public class JwtUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String extractKeycloakId(String bearerToken) {
        try {
            JsonNode node = decodePayload(bearerToken);
            return node.has("sub") ? node.get("sub").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isAdmin(String bearerToken) {
        try {
            JsonNode node = decodePayload(bearerToken);
            JsonNode roles = node.path("realm_access").path("roles");
            if (roles.isArray()) {
                for (JsonNode role : roles) {
                    if ("ADMIN".equalsIgnoreCase(role.asText())) return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static String extractGithubToken(String bearerToken) {
        try {
            JsonNode node = decodePayload(bearerToken);
            return node.has("githubToken") ? node.get("githubToken").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractGitlabToken(String bearerToken) {
        try {
            JsonNode node = decodePayload(bearerToken);
            return node.has("gitlabToken") ? node.get("gitlabToken").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ✅ Méthode centrale — évite la duplication
    private static JsonNode decodePayload(String bearerToken) throws Exception {
        String token = bearerToken.replace("Bearer ", "").trim();
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return mapper.readTree(payload);
    }
}