# Étape 1 — Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Étape 2 — Run
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# ✅ §3.7 — Outils nécessaires à l'analyse de qualité de code SonarQube :
#   - git    : pour cloner temporairement le code source du repo à analyser
#   - curl/unzip : pour télécharger et installer le SonarScanner CLI
#   - bash   : le script sonar-scanner fourni par SonarSource attend bash, pas sh
RUN apk add --no-cache git curl unzip bash
RUN git config --system http.version HTTP/1.1
RUN git config --system http.postBuffer 524288000


ENV SONAR_SCANNER_VERSION=5.0.1.3006
RUN curl -sSfo /tmp/sonar-scanner.zip \
      https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-${SONAR_SCANNER_VERSION}-linux.zip \
    && unzip -q /tmp/sonar-scanner.zip -d /opt \
    && mv /opt/sonar-scanner-${SONAR_SCANNER_VERSION}-linux /opt/sonar-scanner \
    && rm /tmp/sonar-scanner.zip \
    # ✅ Remplace le lien/fichier existant par un lien valide vers le Java de l'image
    && rm -f /opt/sonar-scanner/jre/bin/java \
    && mkdir -p /opt/sonar-scanner/jre/bin \
    && ln -sf "$(which java)" /opt/sonar-scanner/jre/bin/java

ENV PATH="/opt/sonar-scanner/bin:${PATH}"
ENV SONAR_SCANNER_JAVA_PATH="/opt/java/openjdk/bin/java"
ENV SONAR_SCANNER_OPTS="-Djava.net.useSystemProxies=true"

COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]