package devmedic.gestiongit.Services;

import devmedic.gestiongit.Entities.Branch;
import devmedic.gestiongit.Repos.BranchRep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchServ {

    private final BranchRep branchRep;

    public List<Branch> getAll() {
        return branchRep.findAll();
    }

    public List<Branch> getByUserId(Long userId) {
        return branchRep.findByRepositoryUserIdsContaining(userId);
    }

    public List<Branch> getByRepository(Long repositoryId) {
        return branchRep.findByRepository_Id(repositoryId);
    }

    public Branch getById(Long id) {
        return branchRep.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
    }

    public Branch getDefaultBranch(Long repositoryId) {
        return branchRep.findByRepository_IdAndIsDefault(repositoryId, true)
                .orElseThrow(() -> new RuntimeException("Default branch not found"));
    }

    public long getTotalCount() {
        return branchRep.count();
    }

    public long getTotalCountForUser(Long userId) {
        return branchRep.findByRepositoryUserIdsContaining(userId).size();
    }

    public Map<String, Long> getBranchCountByRepository() {
        return branchRep.findAll().stream()
                .collect(Collectors.groupingBy(
                        b -> b.getRepository().getName(), Collectors.counting()
                ));
    }

    public Map<String, Long> getBranchCountByRepositoryForUser(Long userId) {
        return branchRep.findByRepositoryUserIdsContaining(userId).stream()
                .collect(Collectors.groupingBy(
                        b -> b.getRepository().getName(), Collectors.counting()
                ));
    }

    public Map<String, Long> getProtectedVsUnprotected() {
        long def = branchRep.findAll().stream().filter(Branch::isDefault).count();
        return Map.of("default", def, "other", branchRep.count() - def);
    }

    public Map<String, Long> getProtectedVsUnprotectedForUser(Long userId) {
        List<Branch> branches = branchRep.findByRepositoryUserIdsContaining(userId);
        long def = branches.stream().filter(Branch::isDefault).count();
        return Map.of("default", def, "other", branches.size() - def);
    }

    public List<Branch> getActiveBranches(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return branchRep.findAll().stream()
                .filter(b -> b.getLastCommitTime() != null &&
                        b.getLastCommitTime().isAfter(since))
                .collect(Collectors.toList());
    }

    public List<Branch> getActiveBranchesForUser(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return branchRep.findByRepositoryUserIdsContaining(userId).stream()
                .filter(b -> b.getLastCommitTime() != null &&
                        b.getLastCommitTime().isAfter(since))
                .collect(Collectors.toList());
    }

    public List<Branch> getStaleBranches(int days) {
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        return branchRep.findAll().stream()
                .filter(b -> b.getLastCommitTime() != null &&
                        b.getLastCommitTime().isBefore(before))
                .collect(Collectors.toList());
    }

    public List<Branch> getStaleBranchesForUser(Long userId, int days) {
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        return branchRep.findByRepositoryUserIdsContaining(userId).stream()
                .filter(b -> b.getLastCommitTime() != null &&
                        b.getLastCommitTime().isBefore(before))
                .collect(Collectors.toList());
    }
}