package devmedic.gestiongit.Entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "git_repository")
public class GitRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String remoteId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String owner;

    private String defaultBranchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderType provider;

    @Column(nullable = false)
    private boolean isPrivate;

    private LocalDateTime lastAnalyzedAt;

    @ElementCollection
    @CollectionTable(
            name = "repository_users",
            joinColumns = @JoinColumn(name = "repository_id")
    )
    @Column(name = "user_id")
    private List<Long> userIds = new ArrayList<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Branch> branches = new ArrayList<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Tag> tags = new ArrayList<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<PullRequest> pullRequests = new ArrayList<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Contribution> contributions = new ArrayList<>();

    public String getFullName() {
        return owner + "/" + name;
    }

    public void updateLastAnalyzed() {
        this.lastAnalyzedAt = LocalDateTime.now();
    }

    public void addUserId(Long userId) {
        if (!this.userIds.contains(userId)) {
            this.userIds.add(userId);
        }
    }
}