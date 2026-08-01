package devmedic.gestiongit.Entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "git_predicted_risk",
        uniqueConstraints = @UniqueConstraint(columnNames = "repository_id")
)
public class PredictedRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double probabilityCritical;
    private String riskLevel;          // LOW, MODERATE, HIGH
    private String topFactorsJson;     // stocké en JSON brut pour simplicité
    private LocalDateTime calculatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    @JsonBackReference
    private GitRepository repository;
}