package devmedic.gestiongit.Entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.aspectj.apache.bcel.util.Repository;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "git_risk_score"
)
public class RiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double stabilityScore;
    private int hotspotCount;
    private long technicalDebtMinutes;
    private LocalDateTime calculatedAt;

    // ✅ Clé logique du score : le repository, une seule ligne par repo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    @JsonBackReference
    private GitRepository repository;

    // Référence informative : quel commit a déclenché ce calcul
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_id", nullable = true)
    @JsonBackReference
    private Commit commit;
}