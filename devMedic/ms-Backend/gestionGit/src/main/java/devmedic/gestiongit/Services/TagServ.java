package devmedic.gestiongit.Services;

import devmedic.gestiongit.Entities.Tag;
import devmedic.gestiongit.Repos.TagRep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagServ {

    private final TagRep tagRep;

    public List<Tag> getAll() {
        return tagRep.findAll();
    }

    public List<Tag> getByUserId(Long userId) {
        return tagRep.findByRepositoryUserIdsContaining(userId);
    }

    public List<Tag> getByRepository(Long repositoryId) {
        return tagRep.findByRepository_Id(repositoryId);
    }

    public Tag getById(Long id) {
        return tagRep.findById(id)
                .orElseThrow(() -> new RuntimeException("Tag not found"));
    }

    public long getTotalCount() {
        return tagRep.count();
    }

    public long getTotalCountForUser(Long userId) {
        return tagRep.findByRepositoryUserIdsContaining(userId).size();
    }

    public Map<String, Long> getTagCountByRepository() {
        return tagRep.findAll().stream()
                .collect(Collectors.groupingBy(
                        t -> t.getRepository().getName(), Collectors.counting()
                ));
    }

    public Map<String, Long> getTagCountByRepositoryForUser(Long userId) {
        return tagRep.findByRepositoryUserIdsContaining(userId).stream()
                .collect(Collectors.groupingBy(
                        t -> t.getRepository().getName(), Collectors.counting()
                ));
    }

    public List<Tag> getRecentTags(int limit) {
        return tagRep.findAll().stream()
                .filter(t -> t.getTaggedAt() != null)
                .sorted((a, b) -> b.getTaggedAt().compareTo(a.getTaggedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Tag> getRecentTagsForUser(Long userId, int limit) {
        return tagRep.findByRepositoryUserIdsContaining(userId).stream()
                .filter(t -> t.getTaggedAt() != null)
                .sorted((a, b) -> b.getTaggedAt().compareTo(a.getTaggedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Map<Integer, Long> getTagsByYear() {
        return tagRep.findAll().stream()
                .filter(t -> t.getTaggedAt() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getTaggedAt().getYear(), Collectors.counting()
                ));
    }

    public Map<Integer, Long> getTagsByYearForUser(Long userId) {
        return tagRep.findByRepositoryUserIdsContaining(userId).stream()
                .filter(t -> t.getTaggedAt() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getTaggedAt().getYear(), Collectors.counting()
                ));
    }

    public long countReleaseTags() {
        return tagRep.findAll().stream()
                .filter(t -> t.getName().matches("v?\\d+\\.\\d+.*"))
                .count();
    }

    public long countReleaseTagsForUser(Long userId) {
        return tagRep.findByRepositoryUserIdsContaining(userId).stream()
                .filter(t -> t.getName().matches("v?\\d+\\.\\d+.*"))
                .count();
    }
}