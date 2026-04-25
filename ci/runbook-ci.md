# Runbook DevSecOps — POC Keycloak Spring Boot

## Vue d'ensemble

```
PHASE 1 → Démarrer les outils CI
PHASE 2 → Configurer Jenkins (plugins + outils + credentials)
PHASE 3 → Configurer SonarQube (token + webhook + quality gate)
PHASE 4 → Installer Trivy dans Jenkins
PHASE 5 → Créer le job Jenkins Pipeline
PHASE 6 → Premier run + validation
```

### Architecture du pipeline

```
Checkout
  └── DEV — Build (app) + Tests unitaires (service)  [parallèle]
       └── OWASP Dependency Check                     [CVE ≥ 7 → unstable]
            └── SonarQube Analysis (app + service)
                 └── Quality Gate                     [bloque si KO]
                      └── Trivy Filesystem Scan
                           └── Docker Build            [parallèle : app + service]
                                └── Trivy Image Scan   [avant push]
                                     └── Docker Push
                                          └── UAT — Approbation manuelle + Deploy (port 8091)
                                               └── PREPROD — Approbation manuelle + Deploy (port 8092)
```

---

## PHASE 1 — Démarrer Jenkins + SonarQube

> **Changement important** : Jenkins utilise désormais une image custom avec version fixée
> (`2.462.3-lts-jdk17`), tous les plugins pré-installés, Trivy et Maven intégrés.
> Plus besoin d'installer quoi que ce soit manuellement après le démarrage.

### Étape 1.1 — Prérequis système

Vérifie que Docker tourne et que les ports sont libres :

```bash
# Vérifier Docker
docker version

# Vérifier ports libres (9090 Jenkins, 9000 SonarQube)
lsof -i :9090
lsof -i :9000
```

Si un port est occupé par une ancienne instance Jenkins :
```bash
docker stop jenkins && docker rm jenkins
```

### Étape 1.2 — Nettoyer l'ancienne installation (si existante)

Si tu avais déjà lancé l'ancienne version (`lts-jdk17` sans version fixée) :

```bash
cd "/Users/bichri/Documents/MasterClass Java/poc-openId/poc-Keycloak-main"

# Arrêter et supprimer les anciens conteneurs + volumes Jenkins
# (les volumes SonarQube sont conservés)
docker compose -f ci/docker-compose-ci-tools.yml down
docker volume rm poc-keycloak-main_jenkins_home 2>/dev/null || true
docker rmi jenkins/jenkins:lts-jdk17 2>/dev/null || true
```

### Étape 1.3 — Builder et démarrer les conteneurs

```bash
cd "/Users/bichri/Documents/MasterClass Java/poc-openId/poc-Keycloak-main"

# 1ère fois : builder l'image Jenkins custom (~5-10 min selon connexion)
docker compose -f ci/docker-compose-ci-tools.yml up -d --build

# Suivre la progression du build Jenkins
docker compose -f ci/docker-compose-ci-tools.yml logs -f jenkins
```

Tu dois voir dans les logs Jenkins :
```
[INIT] Compte admin créé — login: admin / admin123
[INIT] JDK-17 configuré → /opt/java/openjdk
[INIT] Maven-3.9 configuré → /opt/maven
```

### Étape 1.4 — Vérifier que tout est UP

```bash
docker compose -f ci/docker-compose-ci-tools.yml ps
```

Tu dois voir `healthy` pour les 3 services (`jenkins`, `sonarqube`, `sonar-db`).
SonarQube peut prendre 2-3 minutes supplémentaires au premier démarrage.

### Étape 1.5 — Vérifier les outils dans Jenkins

```bash
# Confirmer Trivy installé dans le conteneur
docker exec jenkins trivy --version

# Confirmer Maven installé
docker exec jenkins mvn --version
```

---

## PHASE 2 — Configurer Jenkins

### 2.1 — Accès (pas de wizard, pas d'installation de plugins)

L'image custom a tout pré-configuré :
- Compte admin créé automatiquement par le script Groovy
- Tous les plugins déjà installés dans l'image
- JDK-17 et Maven-3.9 déjà configurés

1. Ouvre **http://localhost:9090**
2. Login : `admin` / `admin123`
3. Tu arrives directement sur le Dashboard → **c'est tout pour cette étape**

> Si tu vois encore le wizard, c'est que le volume `jenkins_home` contient
> une ancienne config. Relis l'étape 1.2 (nettoyage des volumes).

### 2.2 — Vérifier que les plugins sont bien installés

**Manage Jenkins → Plugins → Installed plugins**

Vérifie la présence de :
- `OWASP Dependency-Check`
- `SonarQube Scanner`
- `Docker Pipeline`
- `Pipeline Stage View`

Si un plugin manque (cas rare de téléchargement échoué lors du build) :
```bash
# Rebuilder l'image sans cache
docker compose -f ci/docker-compose-ci-tools.yml build --no-cache jenkins
docker compose -f ci/docker-compose-ci-tools.yml up -d jenkins
```

### 2.3 — Vérifier JDK et Maven (déjà configurés automatiquement)

**Manage Jenkins → Tools**

Tu dois voir :
- `JDK-17` pointant sur `/opt/java/openjdk`
- `Maven-3.9` pointant sur `/opt/maven`

Ces deux outils ont été configurés par le script `02-tools.groovy` au démarrage.
Si absents, va dans **Tools** et ajoute-les manuellement avec ces mêmes valeurs.

### 2.4 — Créer les Credentials

**Manage Jenkins → Credentials → System → Global credentials → Add Credentials**

Crée chaque credential dans l'ordre :

**Credential 1 — Docker Hub**
```
Kind     : Username with password
Username : moustaphisene
Password : <ton token Docker Hub>
ID       : dockerhub-credentials
```
> Pour créer un token Docker Hub : hub.docker.com → Account Settings → Security → New Access Token

**Credential 2 — NVD API Key (OWASP)**
```
Kind   : Secret text
Secret : <ta clé NVD>
ID     : nvd-api-key
```
> Créer la clé sur https://nvd.nist.gov/developers/request-an-api-key (gratuit, formulaire rapide).
> Sans clé : le premier scan OWASP peut prendre 30 à 60 minutes.

**Credential 3 — Keycloak URL UAT**
```
Kind   : Secret text
Secret : http://host.docker.internal:8080
ID     : keycloak-url-uat
```
> `host.docker.internal` permet au conteneur Jenkins d'atteindre ton Keycloak local.

**Credential 4 — Keycloak URL PREPROD**
```
Kind   : Secret text
Secret : http://host.docker.internal:8080
ID     : keycloak-url-preprod
```

**Credential 5 — Secret client Keycloak UAT**
```
Kind   : Secret text
Secret : <valeur de KEYCLOAK_CLIENT_SECRET>
ID     : keycloak-client-secret-uat
```

**Credential 6 — Secret client Keycloak PREPROD**
```
Kind   : Secret text
Secret : <valeur de KEYCLOAK_CLIENT_SECRET>
ID     : keycloak-client-secret-preprod
```

---

## PHASE 3 — Configurer SonarQube

### 3.1 — Accès et changement de mot de passe

1. Ouvre **http://localhost:9000**
2. Login : `admin` / `admin`
3. Quand il demande de changer le mot de passe → mets `admin123`

### 3.2 — Générer un token d'authentification

**My Account (coin haut droit) → Security → Generate Tokens**

```
Name    : jenkins-token
Type    : Global Analysis Token
Expires : No expiration
```

Clique **Generate** → **copie immédiatement le token** (il ne sera plus affiché).

### 3.3 — Créer un Webhook vers Jenkins

SonarQube doit notifier Jenkins quand l'analyse est terminée (requis par `waitForQualityGate`).

**Administration → Configuration → Webhooks → Create**

```
Name : Jenkins
URL  : http://jenkins:8080/sonarqube-webhook/
```
> On utilise `jenkins:8080` (réseau Docker interne `ci-net`) et non `localhost:9090`.

Clique **Create**.

### 3.4 — Configurer le Quality Gate

**Quality Gates → Sonar way** (le défaut) contient ces conditions :

| Condition | Seuil |
|---|---|
| Coverage | < 80% → Failed |
| Duplicated Lines | > 3% → Failed |
| Maintainability Rating | > A → Failed |
| Reliability Rating | > A → Failed |
| Security Rating | > A → Failed |

Pour le POC, tu peux créer un Quality Gate assoupli sans condition de coverage :
- **Quality Gates → Create** → Name : `POC Gate`
- Ajoute uniquement : `Security Rating is worse than A`
- Clique **Set as Default**

### 3.5 — Relier Jenkins à SonarQube

Retourne dans Jenkins :

**Manage Jenkins → System → Section "SonarQube servers"**

```
☑  Environment variables
Name        : SonarQube                ← doit correspondre exactement au Jenkinsfile
Server URL  : http://sonarqube:9000    ← réseau Docker interne ci-net
Server authentication token : [Add] → Jenkins
```

Dans le popup **Add credentials** :
```
Kind   : Secret text
Secret : <le token copié au 3.2>
ID     : sonar-token
```

Sélectionne `sonar-token` dans le dropdown → Clique **Save**.

---

## PHASE 4 — Trivy (déjà installé dans l'image)

Trivy est intégré dans l'image Jenkins custom. Aucune action manuelle requise.

Vérification :
```bash
docker exec jenkins trivy --version
# Attendu : trivy version 0.51.1
```

Si pour une raison quelconque Trivy est absent :
```bash
docker exec -u root jenkins \
  curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
  | sh -s -- -b /usr/local/bin v0.51.1
```

---

## PHASE 5 — Créer le job Jenkins Pipeline

### 5.1 — Pousser le code sur GitHub

Le Jenkinsfile doit être accessible depuis Jenkins via Git :

```bash
cd "/Users/bichri/Documents/MasterClass Java/poc-openId/poc-Keycloak-main"

git add Jenkinsfile \
        demo/app-springboot/Dockerfile \
        demo/service-springboot-rest/Dockerfile \
        demo/app-springboot/pom.xml \
        demo/service-springboot-rest/pom.xml \
        demo/app-springboot/owasp-suppressions.xml \
        demo/app-springboot/src/main/resources/application-dev.yml \
        demo/app-springboot/src/main/resources/application-uat.yml \
        demo/app-springboot/src/main/resources/application-preprod.yml \
        ci/docker-compose-ci-tools.yml \
        ci/runbook-ci.md

git commit -m "ci: add Jenkins pipeline, Dockerfiles, SonarQube & OWASP config"
git push origin master
```

### 5.2 — Créer le Pipeline Job dans Jenkins

1. **Dashboard → New Item**
2. Name : `poc-keycloak-pipeline`
3. Type : **Pipeline** → OK

**Section "Build Triggers" :**
- Coche **"GitHub hook trigger for GITScm polling"** *(pour le trigger automatique au push)*

**Section "Pipeline" :**
```
Definition  : Pipeline script from SCM
SCM         : Git
Repository  : https://github.com/moustaphisene/<ton-repo>.git
Branch      : */master
Script Path : Jenkinsfile
```

Si ton repo est privé, ajoute des credentials GitHub (Personal Access Token).

Clique **Save**.

---

## PHASE 6 — Premier run et validation

### 6.1 — Lancer le pipeline

**Dashboard → poc-keycloak-pipeline → Build Now**

Clique sur le build `#1` → **Console Output** pour suivre en live.

### 6.2 — Ordre d'exécution et résultats attendus

```
[✓] Checkout          → clone le repo
[✓] DEV Build         → mvn package (app) + mvn verify (service) en parallèle
[✓] OWASP             → rapport XML généré dans target/
[✓] SonarQube         → analyse envoyée à http://sonarqube:9000
[✓] Quality Gate      → attend le webhook de SonarQube (max 10 min)
[✓] Trivy FS          → scan filesystem, rapport archivé
[✓] Docker Build      → 2 images buildées en parallèle
[✓] Trivy Image       → scan des images avant push
[✓] Docker Push       → push sur Docker Hub (moustaphisene/poc-keycloak-app)
[⏸] UAT Approval     → le pipeline attend ton clic "Approuver" dans Jenkins
[✓] UAT Deploy        → conteneur poc-keycloak-uat démarré sur port 8091
[⏸] PREPROD Approval → idem, timeout 2h
[✓] PREPROD Deploy    → conteneur poc-keycloak-preprod démarré sur port 8092
```

### 6.3 — Valider SonarQube

1. Va sur **http://localhost:9000/projects**
2. Tu dois voir `poc-keycloak-app` et `poc-keycloak-service`
3. Clique sur un projet → **Issues, Coverage, Security Hotspots**

### 6.4 — Valider les rapports OWASP

Dans Jenkins : **Build #1 → Dependency-Check Results** (menu gauche après le build).

### 6.5 — Valider les images Docker

```bash
# Images buildées localement
docker images | grep poc-keycloak

# Conteneurs UAT/PREPROD actifs (après approbation)
docker ps | grep poc-keycloak

# Tester l'app UAT
curl http://localhost:8091/actuator/health
```

---

## Troubleshooting

| Symptôme | Cause probable | Solution |
|---|---|---|
| `Quality Gate timeout` | Webhook mal configuré | Vérifier l'URL `http://jenkins:8080/sonarqube-webhook/` dans SonarQube |
| `trivy: command not found` | Trivy pas installé | Relancer Phase 4 |
| `Maven-3.9 not found` | Nom outil Jenkins incorrect | Manage Jenkins → Tools → vérifier le nom exact `Maven-3.9` |
| `OWASP download lent` | Pas de clé NVD | Normal la première fois — 30-60 min sans clé, 2 min avec clé |
| `docker: permission denied` | Socket Docker non monté | Vérifier le volume `/var/run/docker.sock` dans le docker-compose |
| `Cannot connect to sonarqube` | URL réseau incorrecte | Utiliser `http://sonarqube:9000` (réseau `ci-net`), pas `localhost` |
| `Credentials not found` | ID credential incorrect | Vérifier que l'ID correspond exactement à ce qui est dans le Jenkinsfile |

---

## Récapitulatif des URLs

| Service | URL | Credentials |
|---|---|---|
| Jenkins | http://localhost:9090 | admin / admin123 |
| SonarQube | http://localhost:9000 | admin / admin123 |
| App UAT | http://localhost:8091 | — |
| App PREPROD | http://localhost:8092 | — |

---

## Prochaines étapes

Une fois le pipeline vert de bout en bout :

1. **Notifications email** — décommenter le bloc `emailext` dans le `Jenkinsfile`
2. **Webhook GitHub** — configurer le webhook sur GitHub pour déclencher le pipeline automatiquement à chaque push
3. **CD avec ArgoCD** — ajouter un stage qui met à jour le tag d'image dans un repo GitOps, qu'ArgoCD synchronise vers Kubernetes
4. **Prometheus + Grafana** — monitoring de l'app déployée (alertes, dashboards)