package devmedic.gestionuser.Services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class KeycloakBrokerTokenService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getBrokeredToken(String keycloakUserAccessToken, String provider) {
        String url = authServerUrl + "/realms/" + realm + "/broker/" + provider + "/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(keycloakUserAccessToken);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            return extractAccessToken(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            System.err.println("❌ Pas de lien GitHub pour cet utilisateur");
            return null;
        } catch (HttpClientErrorException.Forbidden e) {
            System.err.println("❌ 403 : rôle broker/read-token manquant ou Store Tokens désactivé");
            return null;
        } catch (Exception e) {
            System.err.println("❌ Erreur broker token: " + e.getMessage());
            return null;
        }
    }

    private String extractAccessToken(String body) {
        if (body == null) return null;
        body = body.trim();

        if (body.startsWith("{")) {
            int idx = body.indexOf("\"access_token\"");
            if (idx == -1) return null;
            int colon = body.indexOf(':', idx);
            int q1 = body.indexOf('"', colon + 1);
            int q2 = body.indexOf('"', q1 + 1);
            if (q1 == -1 || q2 == -1) return null;
            return body.substring(q1 + 1, q2);
        }

        Map<String, String> params = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(
                        URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                );
            }
        }
        return params.get("access_token");
    }
}