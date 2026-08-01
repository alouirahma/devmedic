package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.PredictedRisk;
import devmedic.gestiongit.Services.PredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/git/prediction")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    @GetMapping("/repository/{repoId}")
    public ResponseEntity<?> getLatest(@PathVariable Long repoId) {
        Optional<PredictedRisk> latest = predictionService.getLatest(repoId);

        if (latest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "message", "Aucune prédiction disponible pour ce repository."
            ));
        }

        PredictedRisk p = latest.get();
        return ResponseEntity.ok(Map.of(
                "probabilityCritical", p.getProbabilityCritical(),
                "riskLevel", p.getRiskLevel(),
                "topFactors", p.getTopFactorsJson(),
                "calculatedAt", p.getCalculatedAt()
        ));
    }
}