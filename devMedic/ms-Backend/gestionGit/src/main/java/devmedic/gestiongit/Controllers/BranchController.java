package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.Branch;
import devmedic.gestiongit.Services.BranchServ;
import devmedic.gestiongit.Services.UserClientService;
import devmedic.gestiongit.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchServ branchService;
    private final UserClientService userClientService;

    @GetMapping
    public ResponseEntity<List<Branch>> getAll(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(branchService.getAll());
        return ResponseEntity.ok(branchService.getByUserId(resolveUserId(authHeader)));
    }

    @GetMapping("/repository/{repositoryId}")
    public ResponseEntity<List<Branch>> getByRepository(@PathVariable Long repositoryId) {
        return ResponseEntity.ok(branchService.getByRepository(repositoryId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Branch> getById(@PathVariable Long id) {
        return ResponseEntity.ok(branchService.getById(id));
    }

    @GetMapping("/stats/total")
    public ResponseEntity<Map<String, Long>> getTotalCount(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        long count = isAdmin(authHeader)
                ? branchService.getTotalCount()
                : branchService.getTotalCountForUser(resolveUserId(authHeader));
        return ResponseEntity.ok(Map.of("total", count));
    }

    @GetMapping("/stats/by-repository")
    public ResponseEntity<Map<String, Long>> getBranchCountByRepository(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(branchService.getBranchCountByRepository());
        return ResponseEntity.ok(branchService.getBranchCountByRepositoryForUser(resolveUserId(authHeader)));
    }

    @GetMapping("/stats/protected")
    public ResponseEntity<Map<String, Long>> getProtectedVsUnprotected(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(branchService.getProtectedVsUnprotected());
        return ResponseEntity.ok(branchService.getProtectedVsUnprotectedForUser(resolveUserId(authHeader)));
    }

    @GetMapping("/stats/active")
    public ResponseEntity<List<Branch>> getActiveBranches(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "30") int days) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(branchService.getActiveBranches(days));
        return ResponseEntity.ok(branchService.getActiveBranchesForUser(resolveUserId(authHeader), days));
    }

    @GetMapping("/stats/stale")
    public ResponseEntity<List<Branch>> getStaleBranches(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "90") int days) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(branchService.getStaleBranches(days));
        return ResponseEntity.ok(branchService.getStaleBranchesForUser(resolveUserId(authHeader), days));
    }

    @GetMapping("/stats/default/{repositoryId}")
    public ResponseEntity<Branch> getDefaultBranch(@PathVariable Long repositoryId) {
        return ResponseEntity.ok(branchService.getDefaultBranch(repositoryId));
    }

    private Long resolveUserId(String authHeader) {
        if (authHeader == null) return 1L;
        String keycloakId = JwtUtil.extractKeycloakId(authHeader);
        if (keycloakId == null) return 1L;
        return userClientService.getUserIdByKeycloakId(keycloakId, authHeader.replace("Bearer ", ""));
    }

    private boolean isAdmin(String authHeader) {
        if (authHeader == null) return false;
        return JwtUtil.isAdmin(authHeader);
    }
}