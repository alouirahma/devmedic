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
@Table(name = "git_quality_metric")
public class QualityMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double duplicationPercent;
    private double complexity;
    private double maintainabilityIndex;
    private int codeSmells;
    private LocalDateTime calculatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    @JsonBackReference
    private GitRepository repository;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_id", nullable = true)
    @JsonBackReference
    private Commit commit;
}