# =============================================================================
# Image Jenkins custom — version fixée pour éviter les conflits de plugins
# Jenkins LTS 2.462.3 + JDK 17 + Trivy + Maven 3.9 + plugins pré-installés
# =============================================================================

FROM jenkins/jenkins:2.504-jdk17

USER root

# ---------------------------------------------------------------------------
# 1. Dépendances système
# ---------------------------------------------------------------------------
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
        curl \
        wget \
        unzip \
        ca-certificates \
        gnupg \
        lsb-release \
        apt-transport-https && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# 2. Docker CLI — socket monté dans le compose, mais le binaire doit être
#    installé séparément. Utilise le dépôt officiel Docker (ARM64 + amd64).
# ---------------------------------------------------------------------------
RUN curl -fsSL https://download.docker.com/linux/debian/gpg \
        | gpg --dearmor -o /usr/share/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker.gpg] \
        https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
        | tee /etc/apt/sources.list.d/docker.list && \
    apt-get update -qq && \
    apt-get install -y --no-install-recommends docker-ce-cli && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    docker --version

# ---------------------------------------------------------------------------
# 4. Trivy — via dépôt apt officiel Aqua Security (gère ARM64 et amd64 auto)
# ---------------------------------------------------------------------------
RUN wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key \
        | gpg --dearmor \
        | tee /usr/share/keyrings/trivy.gpg > /dev/null && \
    echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb generic main" \
        | tee /etc/apt/sources.list.d/trivy.list && \
    apt-get update -qq && \
    apt-get install -y --no-install-recommends trivy && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    trivy --version

# ---------------------------------------------------------------------------
# 5. Maven 3.9 — installé directement (pas via tool installer Jenkins)
# ---------------------------------------------------------------------------
ARG MAVEN_VERSION=3.9.6
RUN curl -fsSL \
        "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        | tar -xz -C /opt && \
    ln -s "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven

ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Vérification
RUN mvn --version

# ---------------------------------------------------------------------------
# 6. Plugins Jenkins — pré-installés à l'image build pour éviter les
#    conflits de version au runtime. jenkins-plugin-cli résout les versions
#    compatibles avec la version Jenkins embarquée automatiquement.
# ---------------------------------------------------------------------------
USER jenkins

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt

RUN jenkins-plugin-cli \
        --plugin-file /usr/share/jenkins/ref/plugins.txt \
        --verbose

# ---------------------------------------------------------------------------
# 7. Script d'initialisation — créé le compte admin au 1er démarrage
#    (évite l'assistant de setup interactif)
# ---------------------------------------------------------------------------
COPY init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/