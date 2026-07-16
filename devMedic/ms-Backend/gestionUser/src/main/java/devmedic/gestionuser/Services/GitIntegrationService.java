package devmedic.gestionuser.Services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class GitIntegrationService {

    private final RestTemplate restTemplate;

    // Dans application.properties : git.service.url=http://localhost:8082
    @Value("${git.service.url}")
    private String gitServiceUrl;

    // ✅ Appel HTTP vers gestion-git avec userId + token
    public void importUserGitlab(Long userId, String gitlabToken) {
        String url = gitServiceUrl + "/git/import/gitlab/" + userId + "?token=" + gitlabToken;
        restTemplate.postForObject(url, null, Void.class);
    }
}