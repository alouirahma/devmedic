package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.RiskScore;
import devmedic.gestiongit.Services.RiskAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
@RestController
@RequestMapping("/api/git/risk")
@RequiredArgsConstructor
public class Riskcontroller {
    private final RiskAnalysisService riskAnalysisService;

    @PostMapping("/repository/{repoId}/analyze")
    public ResponseEntity<RiskAnalysisService.RiskAnalysisResult> analyze(
            @PathVariable Long repoId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = authHeader != null ? authHeader.replace("Bearer ", "") : null;
        RiskAnalysisService.RiskAnalysisResult result = riskAnalysisService.analyze(repoId, token);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/repository/{repoId}")
    public ResponseEntity<?> getLatest(@PathVariable Long repoId) {
        Optional<RiskScore> latest = riskAnalysisService.getLatest(repoId);

        if (latest.isEmpty()) {
            // ✅ Pas encore d'analyse de risque calculée pour ce repo
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "message", "Aucune analyse de risque disponible pour ce repository. Lancez une analyse d'abord.",
                    "stabilityScore", 0,
                    "hotspotCount", 0,
                    "technicalDebtMinutes", 0
            ));
        }

        RiskScore score = latest.get();
        return ResponseEntity.ok(Map.of(
                "stabilityScore", score.getStabilityScore(),
                "hotspotCount", score.getHotspotCount(),
                "technicalDebtMinutes", score.getTechnicalDebtMinutes(),
                "calculatedAt", score.getCalculatedAt(),
                "riskLevel", toRiskLevel(score.getStabilityScore())
        ));
    }

    private String toRiskLevel(double stabilityScore) {
        if (stabilityScore >= 70) return "STABLE";
        if (stabilityScore >= 40) return "MODERATE";
        return "CRITICAL";
    }

}

