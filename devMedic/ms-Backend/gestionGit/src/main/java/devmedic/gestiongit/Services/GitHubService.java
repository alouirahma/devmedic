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
public class GitHubService {

    private final GitRepositoryRep gitRepositoryRep;
    private final BranchRep branchRep;
    private final TagRep tagRep;
    private final CommitRep commitRep;
    private final PullRequestRep pullRequestRep;
    private final PushRep pushRep;


    private static final String GITHUB_API = "https://api.github.com";
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpHeaders getHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        return headers;
    }

    // ─── Import Repositories ───────────────────────────────────────────
    public List<GitRepository> importRepositories(Long userId, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/user/repos?per_page=100&sort=updated",
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders(token)),
                    String.class
            );

            JsonNode reposNode = mapper.readTree(response.getBody());
            List<GitRepository> savedRepos = new ArrayList<>();

            for (JsonNode repoNode : reposNode) {
                String remoteId = repoNode.get("id").asText();

                GitRepository gitRepo = gitRepositoryRep
                        .findByRemoteIdAndProvider(remoteId, ProviderType.GITHUB)
                        .orElse(new GitRepository());

                gitRepo.setRemoteId(remoteId);
                gitRepo.setName(repoNode.get("name").asText());
                gitRepo.setOwner(repoNode.get("owner").get("login").asText());
                gitRepo.setDefaultBranchName(repoNode.get("default_branch").asText());
                gitRepo.setPrivate(repoNode.get("private").asBoolean());
                gitRepo.setProvider(ProviderType.GITHUB);
                gitRepo.updateLastAnalyzed();
                gitRepo.addUserId(userId);

                gitRepositoryRep.save(gitRepo);
                savedRepos.add(gitRepo);

                importBranches(gitRepo, token);
                importTags(gitRepo, token);
            }

            return savedRepos;

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'import GitHub: " + e.getMessage(), e);
        }
    }

    // ─── Import Branches ───────────────────────────────────────────────
    public List<Branch> importBranches(GitRepository repo, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + repo.getFullName() + "/branches",
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
                branch.setDefault(name.equals(repo.getDefaultBranchName()));
                branch.setCreatedAt(LocalDateTime.now());
                branch.setLastCommitSha(b.get("commit").get("sha").asText());
                branch.setLastCommitTime(LocalDateTime.now());
                branch.setRepository(repo);

                branchRep.save(branch);
                savedBranches.add(branch);
            }

            return savedBranches;

        } catch (Exception e) {
            throw new RuntimeException("Erreur import branches GitHub: " + e.getMessage(), e);
        }
    }

    // ─── Import Tags ───────────────────────────────────────────────────
    public List<Tag> importTags(GitRepository repo, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + repo.getFullName() + "/tags",
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders(token)),
                    String.class
            );

            JsonNode tagsNode = mapper.readTree(response.getBody());
            List<Tag> savedTags = new ArrayList<>();

            for (JsonNode t : tagsNode) {
                Tag tag = new Tag();
                tag.setName(t.get("name").asText());
                tag.setCommitSha(t.get("commit").get("sha").asText());
                tag.setTaggedAt(LocalDateTime.now());
                tag.setRepository(repo);

                tagRep.save(tag);
                savedTags.add(tag);
            }

            return savedTags;

        } catch (Exception e) {
            throw new RuntimeException("Erreur import tags GitHub: " + e.getMessage(), e);
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
                        GITHUB_API + "/repos/" + repo.getFullName()
                                + "/commits?per_page=100&page=" + page,
                        HttpMethod.GET,
                        new HttpEntity<>(getHeaders(token)),
                        String.class
                );

                JsonNode nodes = mapper.readTree(response.getBody());
                if (!nodes.isArray() || nodes.size() == 0) break;

                for (JsonNode c : nodes) {
                    String sha = c.get("sha").asText();

                    Commit commit = commitRep
                            .findByShaAndBranch_Id(sha, defaultBranch.getId())
                            .orElse(new Commit());

                    commit.setSha(sha);
                    commit.setBranch(defaultBranch); // ✅ setBranch au lieu de setRepository

                    JsonNode commitData = c.get("commit");
                    commit.setMessage(commitData.get("message").asText());

                    JsonNode author = commitData.get("author");
                    commit.setAuthorName(author.get("name").asText());
                    commit.setAuthorEmail(author.get("email").asText());
                    commit.setCommittedAt(LocalDateTime.parse(
                            author.get("date").asText().replace("Z", ""),
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
            throw new RuntimeException("Erreur import commits GitHub: " + e.getMessage(), e);
        }
    }
    // ─── Import Pull Requests ──────────────────────────────────────────
    public List<PullRequest> importPullRequests(GitRepository repo, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            List<PullRequest> saved = new ArrayList<>();

            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + repo.getFullName() + "/pulls?state=all&per_page=100",
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders(token)),
                    String.class
            );

            JsonNode nodes = mapper.readTree(response.getBody());

            for (JsonNode pr : nodes) {
                int number = pr.get("number").asInt();

                PullRequest pullRequest = pullRequestRep
                        .findByNumberAndRepository_Id(number, repo.getId())
                        .orElse(new PullRequest());

                pullRequest.setNumber(number);
                pullRequest.setTitle(pr.get("title").asText());
                pullRequest.setRepository(repo);
                pullRequest.setSourceBranch(pr.get("head").get("ref").asText());
                pullRequest.setTargetBranch(pr.get("base").get("ref").asText());
                pullRequest.setCreatedAt(LocalDateTime.parse(
                        pr.get("created_at").asText().replace("Z", ""),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                ));

                if (pr.has("merged_at") && !pr.get("merged_at").isNull()) {
                    pullRequest.setState(PullRequestState.MERGED);
                    pullRequest.setMergedAt(LocalDateTime.parse(
                            pr.get("merged_at").asText().replace("Z", ""),
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    ));
                } else {
                    pullRequest.setState("open".equals(pr.get("state").asText())
                            ? PullRequestState.OPEN : PullRequestState.CLOSED);
                }

                pullRequestRep.save(pullRequest);
                saved.add(pullRequest);
            }

            return saved;

        } catch (Exception e) {
            throw new RuntimeException("Erreur import PRs GitHub: " + e.getMessage(), e);
        }
    }

    // ─── Import Pushes ─────────────────────────────────────────────────
    public void importPushes(GitRepository repo, String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + repo.getFullName() + "/events?per_page=100",
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders(token)),
                    String.class
            );

            JsonNode events = mapper.readTree(response.getBody());

            for (JsonNode event : events) {
                if (!"PushEvent".equals(event.get("type").asText())) continue;

                JsonNode payload = event.get("payload");
                String branchName = payload.get("ref").asText()
                        .replace("refs/heads/", "");

                branchRep.findByRepository_IdAndName(repo.getId(), branchName)
                        .ifPresent(branch -> {
                            try {
                                Push push = new Push();
                                push.setBranch(branch);
                                push.setCommitCount(payload.get("size").asInt());
                                push.setForcePush(payload.get("forced").asBoolean());
                                push.setHeadCommitSha(payload.get("head").asText());
                                push.setPushedAt(LocalDateTime.parse(
                                        event.get("created_at").asText().replace("Z", ""),
                                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                                ));
                                if (event.has("actor")) {
                                    push.setPusherId(event.get("actor").get("id").asLong());
                                }
                                pushRep.save(push);
                            } catch (Exception ignored) {}
                        });
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur import pushes GitHub: " + e.getMessage(), e);
        }
    }
}