package devmedic.gestiongit.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import devmedic.gestiongit.Entities.*;
import devmedic.gestiongit.Repos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitLabService {

    private final GitRepositoryRep gitRepositoryRep;
    private final BranchRep branchRep;
    private final TagRep tagRep;
    private final CommitRep commitRep;
    private final PullRequestRep pullRequestRep;
    private final PushRep pushRep;


    private static final String GITLAB_API = "https://gitlab.com/api/v4";
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpHeaders getHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        return headers;
    }

    // ─── Import Repositories ───────────────────────────────────────────
    public List<GitRepository> importRepositories(Long userId, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    GITLAB_API + "/projects?membership=true&per_page=100",
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders(token)),
                    String.class
            );

            JsonNode reposNode = mapper.readTree(response.getBody());
            List<GitRepository> savedRepos = new ArrayList<>();

            for (JsonNode repoNode : reposNode) {
                String remoteId = repoNode.get("id").asText();

                GitRepository gitRepo = gitRepositoryRep
                        .findByRemoteIdAndProvider(remoteId, ProviderType.GITLAB)
                        .orElse(new GitRepository());

                gitRepo.setRemoteId(remoteId);
                gitRepo.setName(repoNode.get("name").asText());
                gitRepo.setOwner(repoNode.get("namespace").get("path").asText());
                gitRepo.setPrivate(!"public".equals(repoNode.get("visibility").asText()));

                String defaultBranch = repoNode.has("default_branch")
                        && !repoNode.get("default_branch").isNull()
                        ? repoNode.get("default_branch").asText() : "main";
                gitRepo.setDefaultBranchName(defaultBranch);

                gitRepo.setProvider(ProviderType.GITLAB);
                gitRepo.updateLastAnalyzed();
                gitRepo.addUserId(userId);

                gitRepositoryRep.save(gitRepo);
                savedRepos.add(gitRepo);

                importBranches(gitRepo, token);
                importTags(gitRepo, token);
            }

            return savedRepos;

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'import GitLab: " + e.getMessage(), e);
        }
    }

    // ─── Import Branches ───────────────────────────────────────────────
    public List<Branch> importBranches(GitRepository repo, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    GITLAB_API + "/projects/" + repo.getRemoteId() + "/repository/branches",
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders(token)),
                    String.class
            );

            JsonNode branchesNode = mapper.readTree(response.getBody());
            List<Branch> savedBranches = new ArrayList<>();

            for (JsonNode b : branchesNode) {
                String name = b.get("name").asText();

                Branch branch = branchRep
                        .findByRepository_IdAndName(repo.getId(), name)
                        .orElse(new Branch());

                branch.setName(name);
                branch.setDefault(b.get("default").asBoolean());
                branch.setLastCommitSha(b.get("commit").get("id").asText());
                branch.setLastCommitTime(LocalDateTime.now());
                branch.setRepository(repo);

                branchRep.save(branch);
                savedBranches.add(branch);
            }

            return savedBranches;

        } catch (Exception e) {
            throw new RuntimeException("Erreur import branches GitLab: " + e.getMessage(), e);
        }
    }

    // ─── Import Tags ───────────────────────────────────────────────────
    public List<Tag> importTags(GitRepository repo, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    GITLAB_API + "/projects/" + repo.getRemoteId() + "/repository/tags",
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders(token)),
                    String.class
            );

            JsonNode tagsNode = mapper.readTree(response.getBody());
            List<Tag> savedTags = new ArrayList<>();

            for (JsonNode t : tagsNode) {
                Tag tag = new Tag();
                tag.setName(t.get("name").asText());
                tag.setCommitSha(t.get("commit").get("id").asText());
                tag.setTaggedAt(LocalDateTime.now());
                tag.setRepository(repo);

                tagRep.save(tag);
                savedTags.add(tag);
            }

            return savedTags;

        } catch (Exception e) {
            throw new RuntimeException("Erreur import tags GitLab: " + e.getMessage(), e);
        }
    }

    // ─── Import Commits ────────────────────────────────────────────────
    public List<Commit> importCommits(GitRepository repo, String token) {
        try {
            // ✅ Récupérer la branche par défaut
            Branch defaultBranch = branchRep
                    .findByRepository_IdAndName(repo.getId(), repo.getDefaultBranchName())
                    .orElse(null);

            if (defaultBranch == null) return new ArrayList<>();

            RestTemplate restTemplate = new RestTemplate();
            List<Commit> savedCommits = new ArrayList<>();
            int page = 1;

            while (true) {
                ResponseEntity<String> response = restTemplate.exchange(
                        GITLAB_API + "/projects/" + repo.getRemoteId()
                                + "/repository/commits?per_page=100&page=" + page,
                        HttpMethod.GET,
                        new HttpEntity<>(getHeaders(token)),
                        String.class
                );

                JsonNode nodes = mapper.readTree(response.getBody());
                if (!nodes.isArray() || nodes.size() == 0) break;

                for (JsonNode c : nodes) {
                    String sha = c.get("id").asText();

                    Commit commit = commitRep
                            .findByShaAndBranch_Id(sha, defaultBranch.getId())
                            .orElse(new Commit());

                    commit.setSha(sha);
                    commit.setBranch(defaultBranch); // ✅ setBranch au lieu de setRepository
                    commit.setMessage(c.get("message").asText());
                    commit.setAuthorName(c.get("author_name").asText());
                    commit.setAuthorEmail(c.get("author_email").asText());
                    commit.setCommittedAt(LocalDateTime.parse(
                            c.get("committed_date").asText().substring(0, 19),
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    ));

                    if (c.has("stats")) {
                        commit.setLinesAdded(c.get("stats").get("additions").asInt());
                        commit.setLinesDeleted(c.get("stats").get("deletions").asInt());
                    }

                    commitRep.save(commit);
                    savedCommits.add(commit);
                }

                if (nodes.size() < 100) break;
                page++;
            }

            return savedCommits;

        } catch (Exception e) {
            throw new RuntimeException("Erreur import commits GitLab: " + e.getMessage(), e);
        }
    }

    // ─── Import Pull Requests (Merge Requests) ─────────────────────────
    public List<PullRequest> importPullRequests(GitRepository repo, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            List<PullRequest> saved = new ArrayList<>();

            ResponseEntity<String> response = restTemplate.exchange(
                    GITLAB_API + "/projects/" + repo.getRemoteId()
                            + "/merge_requests?state=all&per_page=100",
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders(token)),
                    String.class
            );

            JsonNode nodes = mapper.readTree(response.getBody());

            for (JsonNode mr : nodes) {
                int number = mr.get("iid").asInt();

                PullRequest pullRequest = pullRequestRep
                        .findByNumberAndRepository_Id(number, repo.getId())
                        .orElse(new PullRequest());

                pullRequest.setNumber(number);
                pullRequest.setTitle(mr.get("title").asText());
                pullRequest.setRepository(repo);
                pullRequest.setSourceBranch(mr.get("source_branch").asText());
                pullRequest.setTargetBranch(mr.get("target_branch").asText());
                pullRequest.setCreatedAt(LocalDateTime.parse(
                        mr.get("created_at").asText().substring(0, 19),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                ));

                pullRequest.setState(switch (mr.get("state").asText()) {
                    case "opened" -> PullRequestState.OPEN;
                    case "merged" -> PullRequestState.MERGED;
                    default -> PullRequestState.CLOSED;
                });

                if (mr.has("merged_at") && !mr.get("merged_at").isNull()) {
                    pullRequest.setMergedAt(LocalDateTime.parse(
                            mr.get("merged_at").asText().substring(0, 19),
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    ));
                }

                pullRequestRep.save(pullRequest);
                saved.add(pullRequest);
            }

            return saved;

        } catch (Exception e) {
            throw new RuntimeException("Erreur import MRs GitLab: " + e.getMessage(), e);
        }
    }

    // ─── Import Pushes ─────────────────────────────────────────────────
    public void importPushes(GitRepository repo, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.exchange(
                    GITLAB_API + "/projects/" + repo.getRemoteId()
                            + "/events?action=pushed&per_page=100",
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders(token)),
                    String.class
            );

            JsonNode events = mapper.readTree(response.getBody());

            for (JsonNode event : events) {
                if (!event.has("push_data")) continue;

                JsonNode pushData = event.get("push_data");
                String branchName = pushData.get("ref").asText();

                branchRep.findByRepository_IdAndName(repo.getId(), branchName)
                        .ifPresent(branch -> {
                            try {
                                Push push = new Push();
                                push.setBranch(branch);
                                push.setCommitCount(pushData.get("commit_count").asInt());
                                push.setHeadCommitSha(pushData.get("commit_to").asText());
                                push.setForcePush(false);
                                push.setPushedAt(LocalDateTime.parse(
                                        event.get("created_at").asText().substring(0, 19),
                                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                                ));
                                push.setPusherId(event.get("author_id").asLong());
                                pushRep.save(push);
                            } catch (Exception ignored) {}
                        });
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur import pushes GitLab: " + e.getMessage(), e);
        }
    }

}