package devmedic.gestiongit.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import devmedic.gestiongit.Entities.*;
import devmedic.gestiongit.Repos.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Service d'analyse de la qualité du code via SonarQube — cahier des charges §3.7.
 *
 * Flux (synchrone, "léger" : pas de file de jobs, pas de stockage permanent du code) :
 *   1. Clone superficiel du repo (git clone --depth 1) dans un dossier temporaire
 *   2. Détection du type de projet (Maven/Gradle/Node/Python) + compilation si Java
 *   3. Génération du fichier sonar-project.properties
 *   4. Exécution du SonarScanner CLI (déjà installé dans l'image Docker)
 *   5. Récupération des métriques via l'API REST SonarQube
 *   6. Sauvegarde dans QualityMetric (une ligne par repository, pas par commit)
 *   7. Suppression du dossier temporaire (aucun résidu disque)
 *
 * Le scan étant potentiellement long (de quelques secondes à quelques minutes selon
 * la taille du repo), cette méthode est appelée de façon isolée (son propre try/catch)
 * par ImportController, exactement comme RiskAnalysisService : un échec ici ne doit
 * jamais faire échouer l'analyse Git globale du repo.
 */
@Service
@RequiredArgsConstructor
public class SonarQubeService {

    private static final Logger log = LoggerFactory.getLogger(SonarQubeService.class);

    private final QualityMetricRep qualityMetricRep;
    private final CommitRep commitRep;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${sonar.url:http://sonarqube:9000}")
    private String sonarUrl;

    @Value("${sonar.token:}")
    private String sonarToken;

    private static final long SCAN_TIMEOUT_MINUTES = 5;
    private static final long COMPILE_TIMEOUT_MINUTES = 5;

    public QualityAnalysisResult analyze(GitRepository repo, String cloneUrl) {
        if (sonarToken == null || sonarToken.isBlank()) {
            throw new IllegalStateException(
                    "Token SonarQube non configuré (variable d'environnement SONAR_TOKEN manquante)");
        }

        String projectKey = "devmedic-" + repo.getProvider().name().toLowerCase() + "-" + repo.getId();
        Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "sonar-scan", String.valueOf(repo.getId()));

        try {
            cleanupDirectory(workDir);
            Files.createDirectories(workDir);

            cloneRepository(cloneUrl, workDir);

            // ✅ Détection du type de projet + compilation si nécessaire (§3.7)
            String javaBinaries = prepareProject(workDir);

            writeSonarProjectProperties(workDir, projectKey, repo.getName(), javaBinaries);
            runSonarScanner(workDir, projectKey);
            QualityAnalysisResult result = fetchMetrics(projectKey);

            saveQualityMetric(repo, result);

            return result;

        } catch (Exception e) {
            log.error(">>> Erreur analyse SonarQube pour {}: {}", repo.getName(), e.getMessage());
            throw new RuntimeException("Erreur SonarQube: " + e.getMessage(), e);
        } finally {
            // ✅ Nettoyage systématique, même en cas d'erreur — pas de résidu disque
            cleanupDirectory(workDir);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 1. Clone superficiel
    // ────────────────────────────────────────────────────────────────────────
    private void cloneRepository(String cloneUrl, Path workDir) throws IOException, InterruptedException {
        log.info(">>> Clone (depth=1) vers {}", workDir);

        ProcessBuilder pb = new ProcessBuilder(
                "git", "clone", "--depth", "1", cloneUrl, workDir.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // ✅ Consomme la sortie pour éviter un blocage du process si le buffer se remplit
        String output = new String(process.getInputStream().readAllBytes());

        boolean finished = process.waitFor(2, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Timeout lors du clone du repository");
        }
        if (process.exitValue() != 0) {
            throw new RuntimeException("Échec du git clone: " + output);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. Détection du type de projet + compilation si nécessaire
    //
    // SonarQube a besoin des classes compilées pour analyser correctement le
    // Java (sonar.java.binaries). Les autres langages (JS/TS, Python, etc.)
    // n'ont pas besoin de compilation préalable pour être scannés.
    // ────────────────────────────────────────────────────────────────────────
    private enum ProjectType { MAVEN, GRADLE, NODE, PYTHON, UNKNOWN }

    private ProjectType detectProjectType(Path workDir) {
        if (Files.exists(workDir.resolve("pom.xml"))) {
            return ProjectType.MAVEN;
        }
        if (Files.exists(workDir.resolve("build.gradle")) || Files.exists(workDir.resolve("build.gradle.kts"))) {
            return ProjectType.GRADLE;
        }
        if (Files.exists(workDir.resolve("package.json"))) {
            return ProjectType.NODE;
        }
        if (Files.exists(workDir.resolve("requirements.txt")) || Files.exists(workDir.resolve("pyproject.toml"))) {
            return ProjectType.PYTHON;
        }
        return ProjectType.UNKNOWN;
    }

    /**
     * Compile le projet si nécessaire (Maven/Gradle) et retourne le chemin des
     * classes compilées à fournir à SonarQube via sonar.java.binaries.
     * Retourne null si aucune compilation n'est nécessaire ou possible —
     * dans ce cas, les fichiers .java seront exclus du scan (voir
     * writeSonarProjectProperties) pour ne jamais bloquer l'analyse.
     */
    private String prepareProject(Path workDir) throws IOException, InterruptedException {
        ProjectType type = detectProjectType(workDir);
        log.info(">>> Type de projet détecté: {}", type);

        return switch (type) {
            case MAVEN -> compileMaven(workDir);
            case GRADLE -> compileGradle(workDir);
            case NODE, PYTHON, UNKNOWN -> null; // pas de compilation Java nécessaire
        };
    }

    private String compileMaven(Path workDir) throws IOException, InterruptedException {
        log.info(">>> Compilation Maven en cours...");

        ProcessBuilder pb = new ProcessBuilder("mvn", "compile", "-q", "-DskipTests");
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(COMPILE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Timeout lors de la compilation Maven");
        }
        if (process.exitValue() != 0) {
            log.warn(">>> Échec compilation Maven, scan sans binaires Java: {}", output);
            return null; // ✅ dégradation propre : le scan continue quand même
        }

        Path classesDir = workDir.resolve("target/classes");
        return Files.exists(classesDir) ? "target/classes" : null;
    }

    private String compileGradle(Path workDir) throws IOException, InterruptedException {
        log.info(">>> Compilation Gradle en cours...");

        Path gradlew = workDir.resolve("gradlew");
        boolean useWrapper = Files.exists(gradlew);
        if (useWrapper) {
            gradlew.toFile().setExecutable(true);
        }

        ProcessBuilder pb = new ProcessBuilder(useWrapper ? "./gradlew" : "gradle", "compileJava", "-q");
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(COMPILE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Timeout lors de la compilation Gradle");
        }
        if (process.exitValue() != 0) {
            log.warn(">>> Échec compilation Gradle, scan sans binaires Java: {}", output);
            return null;
        }

        Path classesDir = workDir.resolve("build/classes/java/main");
        return Files.exists(classesDir) ? "build/classes/java/main" : null;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. Configuration SonarScanner
    // ────────────────────────────────────────────────────────────────────────
    private void writeSonarProjectProperties(Path workDir, String projectKey, String projectName, String javaBinaries) throws IOException {
        String javaBinariesLine = javaBinaries != null
                ? "sonar.java.binaries=" + javaBinaries + "\n"
                : "";
        // Si pas de binaires Java disponibles (compilation échouée, ou projet
        // non-Java), on exclut les .java pour ne jamais bloquer le scan.
        String exclusions = javaBinaries != null
                ? "**/node_modules/**,**/target/**,**/build/**,**/dist/**,**/*.min.js,**/.git/**"
                : "**/*.java,**/node_modules/**,**/target/**,**/build/**,**/dist/**,**/*.min.js,**/.git/**";

        String content = """
                sonar.projectKey=%s
                sonar.projectName=%s
                sonar.sources=.
                sonar.sourceEncoding=UTF-8
                sonar.host.url=%s
                sonar.token=%s
                sonar.exclusions=%s
                %s""".formatted(projectKey, projectName, sonarUrl, sonarToken, exclusions, javaBinariesLine);

        Files.writeString(workDir.resolve("sonar-project.properties"), content);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. Exécution du scan
    // ────────────────────────────────────────────────────────────────────────
    private void runSonarScanner(Path workDir, String projectKey) throws IOException, InterruptedException {
        log.info(">>> Lancement sonar-scanner pour {}", projectKey);

        ProcessBuilder pb = new ProcessBuilder("sonar-scanner");
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());

        boolean finished = process.waitFor(SCAN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Timeout lors du scan SonarQube (> " + SCAN_TIMEOUT_MINUTES + " min)");
        }
        if (process.exitValue() != 0) {
            log.error(">>> Sortie sonar-scanner: {}", output);
            throw new RuntimeException("Échec du scan SonarQube (voir logs backend pour détail)");
        }

        // ✅ SonarQube traite le rapport de façon asynchrone après réception ;
        // on attend un court instant pour laisser le temps au "Compute Engine"
        // de terminer le traitement avant d'interroger l'API des métriques.
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 5. Récupération des métriques via l'API SonarQube
    // ────────────────────────────────────────────────────────────────────────
    private QualityAnalysisResult fetchMetrics(String projectKey) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(sonarToken, "");

        String metricKeys = "duplicated_lines_density,complexity,sqale_index,code_smells,ncloc,bugs,vulnerabilities";
        String url = sonarUrl + "/api/measures/component?component=" + projectKey + "&metricKeys=" + metricKeys;

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
        );

        try {
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode measures = root.path("component").path("measures");

            double duplicationPercent = getMetricValue(measures, "duplicated_lines_density", 0.0);
            double complexity = getMetricValue(measures, "complexity", 0.0);
            // sqale_index = dette technique en minutes, fournie directement par Sonar
            long technicalDebtMinutes = (long) getMetricValue(measures, "sqale_index", 0.0);
            int codeSmells = (int) getMetricValue(measures, "code_smells", 0.0);
            int bugs = (int) getMetricValue(measures, "bugs", 0.0);
            int vulnerabilities = (int) getMetricValue(measures, "vulnerabilities", 0.0);
            int linesOfCode = (int) getMetricValue(measures, "ncloc", 0.0);

            // ✅ Indice de maintenabilité simplifié (0-100) dérivé de la densité de
            // duplication et du nombre de code smells rapporté au volume de code.
            // SonarQube ne fournit pas cet indice directement (il fournit une note
            // A-E), donc on le calcule pour rester cohérent avec le champ déjà
            // présent dans l'entité QualityMetric existante.
            double smellDensity = linesOfCode > 0 ? (codeSmells * 1000.0 / linesOfCode) : 0;
            double maintainabilityIndex = Math.max(0, 100 - duplicationPercent - smellDensity);

            return new QualityAnalysisResult(
                    duplicationPercent, complexity, maintainabilityIndex,
                    codeSmells, technicalDebtMinutes, bugs, vulnerabilities, linesOfCode
            );

        } catch (Exception e) {
            throw new RuntimeException("Erreur de parsing des métriques SonarQube: " + e.getMessage(), e);
        }
    }

    private double getMetricValue(JsonNode measures, String key, double defaultValue) {
        if (measures == null || !measures.isArray()) return defaultValue;
        for (JsonNode m : measures) {
            if (key.equals(m.path("metric").asText())) {
                return m.path("value").asDouble(defaultValue);
            }
        }
        return defaultValue;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 6. Sauvegarde — un seul QualityMetric par repository (pas par commit)
    // ────────────────────────────────────────────────────────────────────────
    private void saveQualityMetric(GitRepository repo, QualityAnalysisResult result) {
        List<Commit> commits = commitRep.findByBranch_Repository_Id(repo.getId());

        Commit latestCommit = null;
        if (!commits.isEmpty()) {
            latestCommit = commits.stream()
                    .max(Comparator.comparing(Commit::getCommittedAt))
                    .orElse(commits.get(0));
        }

        QualityMetric metric = qualityMetricRep.findByRepository_Id(repo.getId())
                .orElse(new QualityMetric());

        metric.setDuplicationPercent(result.getDuplicationPercent());
        metric.setComplexity(result.getComplexity());
        metric.setMaintainabilityIndex(result.getMaintainabilityIndex());
        metric.setCodeSmells(result.getCodeSmells());
        metric.setCalculatedAt(LocalDateTime.now());
        metric.setCommit(latestCommit); // référence informative, peut être null
        metric.setRepository(repo);      // ✅ clé logique

        qualityMetricRep.save(metric);
    }

    /** Récupère la dernière analyse qualité sans relancer de scan. */
    public Optional<QualityMetric> getLatest(Long repoId) {
        return qualityMetricRep.findByRepository_Id(repoId);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Nettoyage
    // ────────────────────────────────────────────────────────────────────────
    private void cleanupDirectory(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.warn(">>> Impossible de nettoyer {}: {}", dir, e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Résultat
    // ────────────────────────────────────────────────────────────────────────
    public static class QualityAnalysisResult {

        private double duplicationPercent;
        private double complexity;
        private double maintainabilityIndex;
        private int codeSmells;
        private long technicalDebtMinutes;
        private int bugs;
        private int vulnerabilities;
        private int linesOfCode;

        public QualityAnalysisResult() {}

        public QualityAnalysisResult(double duplicationPercent, double complexity, double maintainabilityIndex,
                                     int codeSmells, long technicalDebtMinutes, int bugs,
                                     int vulnerabilities, int linesOfCode) {
            this.duplicationPercent = duplicationPercent;
            this.complexity = complexity;
            this.maintainabilityIndex = maintainabilityIndex;
            this.codeSmells = codeSmells;
            this.technicalDebtMinutes = technicalDebtMinutes;
            this.bugs = bugs;
            this.vulnerabilities = vulnerabilities;
            this.linesOfCode = linesOfCode;
        }

        public double getDuplicationPercent() { return duplicationPercent; }
        public void setDuplicationPercent(double duplicationPercent) { this.duplicationPercent = duplicationPercent; }

        public double getComplexity() { return complexity; }
        public void setComplexity(double complexity) { this.complexity = complexity; }

        public double getMaintainabilityIndex() { return maintainabilityIndex; }
        public void setMaintainabilityIndex(double maintainabilityIndex) { this.maintainabilityIndex = maintainabilityIndex; }

        public int getCodeSmells() { return codeSmells; }
        public void setCodeSmells(int codeSmells) { this.codeSmells = codeSmells; }

        public long getTechnicalDebtMinutes() { return technicalDebtMinutes; }
        public void setTechnicalDebtMinutes(long technicalDebtMinutes) { this.technicalDebtMinutes = technicalDebtMinutes; }

        public int getBugs() { return bugs; }
        public void setBugs(int bugs) { this.bugs = bugs; }

        public int getVulnerabilities() { return vulnerabilities; }
        public void setVulnerabilities(int vulnerabilities) { this.vulnerabilities = vulnerabilities; }

        public int getLinesOfCode() { return linesOfCode; }
        public void setLinesOfCode(int linesOfCode) { this.linesOfCode = linesOfCode; }
    }
}