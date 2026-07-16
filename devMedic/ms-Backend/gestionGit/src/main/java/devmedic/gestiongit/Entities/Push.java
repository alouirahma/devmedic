package devmedic.gestiongit.Entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


    @Entity
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Table(name = "git_push")
    public class Push {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private Long pusherId;
        private LocalDateTime pushedAt;
        private int commitCount;
        private String headCommitSha;
        private boolean isForcePush;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "branch_id", nullable = false)
        @JsonBackReference
        private Branch branch;
    }

