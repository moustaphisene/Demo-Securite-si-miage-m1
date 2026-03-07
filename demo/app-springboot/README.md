# Demo App Spring Boot — Sécurisation avec Keycloak (OpenID Connect)

Application de démonstration pédagogique montrant comment sécuriser une application web Spring Boot avec **Keycloak** via le protocole **OpenID Connect (OIDC)**.

---

## Table des matières

- [Architecture](#architecture)
- [Prérequis](#prérequis)
- [Installation et lancement](#installation-et-lancement)
- [Configuration Keycloak](#configuration-keycloak)
- [Structure du projet](#structure-du-projet)
- [Concepts clés expliqués](#concepts-clés-expliqués)
- [Flux d'authentification](#flux-dauthentification)
- [Routes de l'application](#routes-de-lapplication)
- [Contrôle d'accès par rôle](#contrôle-daccès-par-rôle)
- [Gestion du secret client](#gestion-du-secret-client)
- [Problèmes fréquents](#problèmes-fréquents)

---

## Architecture

```
┌─────────────────┐        ┌──────────────────┐        ┌─────────────────┐
│   Navigateur    │◄──────►│  app-springboot  │◄──────►│    Keycloak     │
│   (port 8090)   │        │   (port 8090)    │        │   (port 8080)   │
└─────────────────┘        └────────┬─────────┘        └─────────────────┘
                                    │ Bearer Token
                                    ▼
                           ┌──────────────────┐
                           │  Service Planets │
                           │   (port 8091)    │
                           └──────────────────┘
```

L'application joue **deux rôles simultanément** :
- **Client OAuth2** : redirige l'utilisateur vers Keycloak pour la connexion (flux navigateur)
- **Resource Server** : accepte et valide les JWT Bearer tokens (flux API)

---

## Prérequis

| Outil | Version minimale |
|-------|-----------------|
| Java | 17 |
| Maven | 3.8+ |
| Keycloak | 21+ |
| Service Planets | en cours d'exécution sur le port 8091 |

---

## Installation et lancement

### 1. Cloner le projet

```bash
git clone <url-du-repo>
cd demo/app-springboot
```

### 2. Configurer la variable d'environnement du secret

Le secret client ne doit **jamais** être écrit en dur dans le code source.
Récupérez-le depuis la console Keycloak (voir [Configuration Keycloak](#configuration-keycloak)) puis exportez-le :

```bash
export KEYCLOAK_CLIENT_SECRET=<votre_secret_copié_depuis_keycloak>
```

### 3. Lancer l'application

```bash
./mvnw spring-boot:run
```

L'application démarre sur **http://localhost:8090**

---

## Configuration Keycloak

### Étape 1 — Créer un Realm

1. Connectez-vous à la console Keycloak : `http://localhost:8080`
2. Menu **Realm** → **Create Realm**
3. Nom du realm : `bzhcamp`

### Étape 2 — Créer un Client

1. Dans le realm `bzhcamp`, aller dans **Clients** → **Create client**
2. Remplir les champs :

| Champ | Valeur |
|-------|--------|
| Client type | `OpenID Connect` |
| Client ID | `mba-cdsd-app` |
| Name | `Demo Spring Boot App` |

3. Onglet suivant → activer **Client authentication** (ON)
4. **Valid redirect URIs** : `http://localhost:8090/*`
5. **Web origins** : `http://localhost:8090`
6. Sauvegarder

### Étape 3 — Récupérer le secret client

1. Aller dans l'onglet **Credentials** du client
2. Copier la valeur de **Client secret**
3. L'exporter comme variable d'environnement (voir étape 2 du lancement)

### Étape 4 — Créer les rôles du Realm

1. Aller dans **Realm roles** → **Create role**
2. Créer le rôle : `user`
3. (Optionnel pour les tests admin) Créer le rôle : `admin`

### Étape 5 — Créer un utilisateur de test

1. Aller dans **Users** → **Create new user**
2. Remplir :
   - Username : `testuser`
   - Email : `testuser@example.com`
   - First name / Last name : au choix
3. Onglet **Credentials** → **Set password** (décocher "Temporary")
4. Onglet **Role mapping** → **Assign role** → sélectionner `user`

### Étape 6 — Configurer le mapper de rôles (optionnel mais recommandé)

Pour que les rôles apparaissent dans le `userinfo` (nécessaire pour le mapper Spring) :

1. Aller dans **Clients** → `mba-cdsd-app` → **Client scopes**
2. Cliquer sur le scope dédié (ex. `mba-cdsd-app-dedicated`)
3. **Add mapper** → **By configuration** → **User Realm Role**
4. Configurer :
   - Name : `realm roles`
   - Token Claim Name : `realm_access.roles`
   - Add to userinfo : **ON**

---

## Structure du projet

```
src/main/java/.../appspringboot/
│
├── AppSpringbootApplication.java       # Point d'entrée Spring Boot
│
├── config/
│   ├── SecurityConfig.java             # Configuration centrale de la sécurité
│   └── KeycloakLogoutHandler.java      # Déconnexion back-channel vers Keycloak
│
├── service/
│   └── PlanetsService.java             # Appel HTTP vers le service planets (avec token)
│
└── web/
    └── PlanetsController.java          # Contrôleur MVC (routes HTTP)

src/main/resources/
├── application.yml                     # Configuration de l'application
└── templates/
    ├── landing.ftlh                    # Page d'accueil (publique)
    ├── planets.ftlh                    # Page des planètes (authentifiée)
    ├── about.ftlh                      # Page about (publique)
    └── error.ftlh                      # Page d'erreur
```

---

## Concepts clés expliqués

### OAuth2 vs OpenID Connect

| Protocole | Rôle | Token retourné |
|-----------|------|---------------|
| OAuth2 | Autorisation (accès à des ressources) | Access Token |
| OpenID Connect (OIDC) | Authentification (qui est l'utilisateur) | ID Token + Access Token |

Cette application utilise **OIDC** : elle a besoin de connaître l'identité de l'utilisateur, pas seulement ses droits d'accès.

---

### Les deux modes de sécurité Spring

#### 1. OAuth2 Login (flux navigateur)

```
Navigateur → /planets → Spring Security détecte non-authentifié
    → Redirect vers Keycloak login
    → L'utilisateur entre ses credentials
    → Keycloak renvoie un Authorization Code
    → Spring échange le code contre un Access Token + ID Token
    → L'utilisateur est connecté, sa session est stockée côté serveur
```

Configuré par : `http.oauth2Login(Customizer.withDefaults())`

#### 2. Resource Server JWT (flux API)

```
Client API → /planets avec Header "Authorization: Bearer <jwt>"
    → Spring valide la signature du JWT avec la clé publique Keycloak
    → Si valide : accès autorisé
    → Pas de session, pas de redirect
```

Configuré par : `http.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`

---

### Propagation du token vers les services aval

Le `WebClient` est configuré avec `ServletOAuth2AuthorizedClientExchangeFilterFunction` :

```java
// Dans SecurityConfig.java
WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
    ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
        new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    oauth2Client.setDefaultClientRegistrationId("keycloak");
    return WebClient.builder()
        .apply(oauth2Client.oauth2Configuration())
        .build();
}
```

Cela signifie que lorsque `PlanetsService` appelle `http://localhost:8091/planets`, le token OAuth2 de l'utilisateur connecté est **automatiquement ajouté** dans le header `Authorization: Bearer <token>`.

---

### Mapping des rôles Keycloak → Spring Security

Keycloak retourne les rôles dans le claim JWT `realm_access.roles` :

```json
{
  "realm_access": {
    "roles": ["user", "offline_access", "uma_authorization"]
  }
}
```

Le `userAuthoritiesMapperForKeycloak` dans `SecurityConfig` convertit ces rôles en `GrantedAuthority` Spring :

```
"user"  →  ROLE_user
"admin" →  ROLE_admin
```

Ces autorités sont ensuite utilisables avec `@PreAuthorize("hasRole('user')")`.

---

### Back-channel logout

Quand l'utilisateur clique "Logout" dans l'application, deux choses se passent :
1. Spring invalide la session locale
2. `KeycloakLogoutHandler` appelle l'endpoint Keycloak pour invalider le token côté serveur :

```
GET http://localhost:8080/realms/bzhcamp/protocol/openid-connect/logout
    ?id_token_hint=<id_token_de_l_utilisateur>
```

Sans cette étape, l'utilisateur serait déconnecté de l'application mais toujours considéré comme connecté par Keycloak.

---

## Routes de l'application

| Route | Accès | Description |
|-------|-------|-------------|
| `GET /` | Public | Page d'accueil |
| `GET /about` | Public | Page about |
| `GET /planets` | Authentifié + rôle `user` | Liste des planètes |
| `GET /logout` | Authentifié | Déconnexion locale + Keycloak |
| `OPTIONS /planets*` | Public | Requêtes preflight CORS |

---

## Contrôle d'accès par rôle

### Niveau HTTP (SecurityConfig)

```java
// Dans SecurityConfig.java — règle globale sur l'URL
.requestMatchers(new AntPathRequestMatcher("/planets*"))
.authenticated()
```

### Niveau méthode (@PreAuthorize)

```java
// Dans PlanetsController.java — règle fine sur la méthode
@PreAuthorize("hasRole('user')")
public String handlePlanetsRequest(...) { ... }
```

`@EnableMethodSecurity` dans `SecurityConfig` active cette fonctionnalité.

**Exemples d'expressions `@PreAuthorize` :**

```java
@PreAuthorize("hasRole('admin')")                    // un seul rôle
@PreAuthorize("hasAnyRole('admin', 'user')")         // plusieurs rôles (OR)
@PreAuthorize("hasRole('admin') and hasRole('user')")// plusieurs rôles (AND)
@PreAuthorize("isAuthenticated()")                   // juste connecté
@PreAuthorize("#username == authentication.name")    // logique métier
```

---

## Gestion du secret client

### Où est stocké le secret ?

Après les bonnes pratiques appliquées dans ce projet, le secret **n'est plus écrit dans le code source**.

**Dans `application.yml`** — uniquement une référence à une variable d'environnement :

```yaml
client-secret: ${KEYCLOAK_CLIENT_SECRET}
```

Spring Boot lit cette variable au démarrage. Si elle est absente, l'application refuse de démarrer avec l'erreur :
```
Could not resolve placeholder 'KEYCLOAK_CLIENT_SECRET'
```

**La valeur réelle** vit dans votre environnement shell :

```bash
# Définir le secret (à relancer à chaque nouvelle session terminal)
export KEYCLOAK_CLIENT_SECRET=<votre_secret>

# Vérifier qu'il est bien défini
echo $KEYCLOAK_CLIENT_SECRET
```

**La source de vérité : la console Keycloak**

```
http://localhost:8080
→ Realm bzhcamp
→ Clients → mba-cdsd-app
→ Onglet Credentials → Client secret
```

C'est Keycloak qui génère et stocke le secret. Vous pouvez le régénérer à tout moment depuis cet onglet (pensez à mettre à jour votre variable d'environnement en conséquence).

---

### Pourquoi ne pas mettre le secret dans le code ?

Si le secret est commité dans Git, il devient accessible à quiconque a accès au dépôt (collaborateurs, forks publics, historique Git). Un attaquant pourrait alors :
- Usurper l'identité de l'application auprès de Keycloak
- Obtenir des tokens au nom de vrais utilisateurs

> **Règle absolue** : un secret dans Git est un secret compromis.

Si cela arrive par accident, **régénérez immédiatement** le secret dans la console Keycloak.

---

### Où stocker le secret selon l'environnement ?

| Environnement | Solution recommandée |
|---|---|
| Local / dev | Variable d'environnement shell (`export`) |
| Docker | `docker run -e KEYCLOAK_CLIENT_SECRET=...` |
| Docker Compose | Section `environment:` dans `docker-compose.yml` |
| Kubernetes | `Secret` Kubernetes monté en variable d'env |
| CI/CD (GitHub Actions, GitLab CI) | Secrets chiffrés du pipeline |
| Cloud (AWS, Azure, GCP) | Secrets Manager, Key Vault, Secret Manager |

**Exemple Docker Compose :**

```yaml
services:
  app:
    image: app-springboot
    environment:
      - KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET}
```

Le `.env` local (non commité) fournit la valeur, Docker Compose l'injecte dans le conteneur.

**Exemple `application-local.yml` (à ajouter dans `.gitignore`) :**

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-secret: mon_vrai_secret_local
```

Spring Boot charge automatiquement ce fichier en profil `local` (`--spring.profiles.active=local`), sans jamais l'exposer dans Git.

---

## Problèmes fréquents

### L'application ne démarre pas : `Could not resolve placeholder 'KEYCLOAK_CLIENT_SECRET'`

La variable d'environnement n'est pas définie. Exécuter :
```bash
export KEYCLOAK_CLIENT_SECRET=<votre_secret>
```

### Erreur 401 sur `/planets` malgré connexion

Le rôle `user` n'est pas assigné à votre utilisateur dans Keycloak.
Aller dans **Users** → votre utilisateur → **Role mapping** → assigner le rôle `user`.

### Erreur 302 / boucle de redirection

Vérifier que l'URI de redirection dans Keycloak est bien `http://localhost:8090/*`.

### Les rôles ne sont pas reconnus par Spring

Vérifier que le mapper "User Realm Role" est configuré avec **Add to userinfo : ON** (voir [Étape 6](#étape-6----configurer-le-mapper-de-rôles-optionnel-mais-recommandé)).

### Le service planets retourne une erreur

Vérifier que le service tourne bien sur le port 8091 et accepte les requêtes avec Bearer token.

---

## Dépendances Maven principales

| Dépendance | Rôle |
|------------|------|
| `spring-boot-starter-security` | Socle Spring Security |
| `spring-boot-starter-oauth2-client` | Flux OAuth2 Login (navigateur) |
| `spring-boot-starter-oauth2-resource-server` | Validation JWT Bearer token |
| `spring-boot-starter-webflux` | WebClient pour appels HTTP sortants |
| `spring-boot-starter-freemarker` | Moteur de templates HTML |