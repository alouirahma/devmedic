package devmedic.gestiongit.Entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "git_commit")
public class Commit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sha;

    @Column(nullable = false)
    private String authorName;

    private String authorEmail;

    private LocalDateTime committedAt;

    @Column(columnDefinition = "TEXT")
    private String message;

    private int linesAdded;

    private int linesDeleted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    @JsonBackReference
    private Branch branch;

    @OneToOne(mappedBy = "commit", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private QualityMetric qualityMetric;

    @OneToOne(mappedBy = "commit", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private RiskScore riskScore;
}