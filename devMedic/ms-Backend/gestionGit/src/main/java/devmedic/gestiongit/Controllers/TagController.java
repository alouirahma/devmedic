package devmedic.gestiongit.Controllers;

import devmedic.gestiongit.Entities.Tag;
import devmedic.gestiongit.Services.TagServ;
import devmedic.gestiongit.Services.UserClientService;
import devmedic.gestiongit.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagServ tagService;
    private final UserClientService userClientService;

    @GetMapping
    public ResponseEntity<List<Tag>> getAll(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(tagService.getAll());
        return ResponseEntity.ok(tagService.getByUserId(resolveUserId(authHeader)));
    }

    @GetMapping("/repository/{repositoryId}")
    public ResponseEntity<List<Tag>> getByRepository(@PathVariable Long repositoryId) {
        return ResponseEntity.ok(tagService.getByRepository(repositoryId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tag> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.getById(id));
    }

    @GetMapping("/stats/total")
    public ResponseEntity<Map<String, Long>> getTotalCount(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        long count = isAdmin(authHeader)
                ? tagService.getTotalCount()
                : tagService.getTotalCountForUser(resolveUserId(authHeader));
        return ResponseEntity.ok(Map.of("total", count));
    }

    @GetMapping("/stats/by-repository")
    public ResponseEntity<Map<String, Long>> getTagCountByRepository(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(tagService.getTagCountByRepository());
        return ResponseEntity.ok(tagService.getTagCountByRepositoryForUser(resolveUserId(authHeader)));
    }

    @GetMapping("/stats/recent")
    public ResponseEntity<List<Tag>> getRecentTags(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "10") int limit) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(tagService.getRecentTags(limit));
        return ResponseEntity.ok(tagService.getRecentTagsForUser(resolveUserId(authHeader), limit));
    }

    @GetMapping("/stats/by-year")
    public ResponseEntity<Map<Integer, Long>> getTagsByYear(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (isAdmin(authHeader)) return ResponseEntity.ok(tagService.getTagsByYear());
        return ResponseEntity.ok(tagService.getTagsByYearForUser(resolveUserId(authHeader)));
    }

    @GetMapping("/stats/release-tags")
    public ResponseEntity<Map<String, Long>> countReleaseTags(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        long count = isAdmin(authHeader)
                ? tagService.countReleaseTags()
                : tagService.countReleaseTagsForUser(resolveUserId(authHeader));
        return ResponseEntity.ok(Map.of("releaseTags", count));
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