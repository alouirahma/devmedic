package devmedic.gestiongit.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import devmedic.gestiongit.Entities.GitRepository;
import devmedic.gestiongit.Entities.PredictedRisk;
import devmedic.gestiongit.Repos.PredictedRiskRep;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service d'appel au microservice Python de prédiction de risque (IA prédictive).
 *
 * Combine les résultats déjà calculés de RiskAnalysisService (bus factor,
 * hotspots, stabilityScore) et SonarQubeService (complexité, duplication,
 * bugs) pour interroger le modèle ML (Random Forest), qui retourne une
 * probabilité que le repo devienne CRITICAL dans les 30 prochains jours.
 *
 * Comme SonarQubeService et RiskAnalysisService, cette méthode ne doit jamais
 * faire échouer l'analyse Git globale : tout échec (microservice indisponible,
 * timeout) est capturé et journalisé, sans propager d'exception.
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final PredictedRiskRep predictedRiskRep;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${prediction.service.url:http://ia-prediction:8000}")
    private String predictionServiceUrl;

    /**
     * @param risk    résultat de RiskAnalysisService.analyze() — peut être null si le calcul a échoué
     * @param quality résultat de SonarQubeService.analyze() — peut être null si le scan a échoué
     */
    public PredictedRisk predict(GitRepository repo,
                                 RiskAnalysisService.RiskAnalysisResult risk,
                                 SonarQubeService.QualityAnalysisResult quality) {

        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> payload = buildPayload(risk, quality);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    predictionServiceUrl + "/predict",
                    new HttpEntity<>(payload, headers),
                    Map.class
            );

            if (response == null) {
                throw new IllegalStateException("Réponse vide du microservice de prédiction");
            }

            PredictedRisk predicted = predictedRiskRep.findByRepository_Id(repo.getId())
                    .orElse(new PredictedRisk());

            predicted.setRepository(repo);
            predicted.setProbabilityCritical(((Number) response.get("probability_critical")).doubleValue());
            predicted.setRiskLevel((String) response.get("risk_level"));
            predicted.setTopFactorsJson(mapper.writeValueAsString(response.get("top_factors")));
            predicted.setCalculatedAt(LocalDateTime.now());

            return predictedRiskRep.save(predicted);

        } catch (Exception e) {
            log.error(">>> Erreur appel service de prédiction pour {}: {}", repo.getName(), e.getMessage());
            return null; // dégradation propre : ne bloque jamais l'analyse principale
        }
    }

    /**
     * Construit le payload attendu par le microservice Python, avec des valeurs
     * par défaut raisonnables quand risk/quality sont null (scan partiellement
     * échoué) pour que la prédiction reste possible même en mode dégradé.
     *
     * ⚠️ RiskAnalysisResult est un RECORD Java : ses accesseurs n'ont PAS le
     * préfixe "get" (risk.stabilityScore(), pas risk.getStabilityScore()).
     * QualityAnalysisResult est une classe classique : accesseurs "get" normaux.
     */
    private Map<String, Object> buildPayload(RiskAnalysisService.RiskAnalysisResult risk,
                                             SonarQubeService.QualityAnalysisResult quality) {
        Map<String, Object> payload = new HashMap<>();

        // ---- Issues de RiskAnalysisService (§3.8) — accesseurs de RECORD ----
        payload.put("hotspot_count", risk != null ? risk.hotspotCount() : 0);
        payload.put("technical_debt_minutes",
                risk != null ? (double) risk.technicalDebtMinutes() : 0.0);
        payload.put("stability_score", risk != null ? risk.stabilityScore() : 50.0);

        // Le bus factor n'est pas exposé directement dans RiskAnalysisResult —
        // valeur par défaut prudente (cohérente avec un contexte académique où
        // le bus factor est souvent faible).
        payload.put("bus_factor", 2);
        payload.put("commit_count", 25);

        // ---- Issues de SonarQubeService (§3.7) — accesseurs "get" classiques ----
        payload.put("lines_of_code", quality != null ? (double) quality.getLinesOfCode() : 3000.0);
        payload.put("reliability_issues", quality != null ? (double) quality.getBugs() : 0.0);
        payload.put("security_issues", quality != null ? (double) quality.getVulnerabilities() : 0.0);
        payload.put("code_smells", quality != null ? (double) quality.getCodeSmells() : 0.0);
        payload.put("duplication_percent", quality != null ? quality.getDuplicationPercent() : 0.0);
        payload.put("complexity", quality != null ? quality.getComplexity() : 0.0);
        payload.put("test_coverage", 0.0); // non exposé par SonarQubeService actuellement

        return payload;
    }

    public Optional<PredictedRisk> getLatest(Long repoId) {
        return predictedRiskRep.findByRepository_Id(repoId);
    }
}