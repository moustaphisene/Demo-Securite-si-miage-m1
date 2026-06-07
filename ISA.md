---
project: poc-keycloak-openid
task: Security hardening loop on the Keycloak/OIDC POC
effort: E3
phase: build
progress: 0/14
mode: loop
iteration: 1
started: 2026-06-07
updated: 2026-06-07
---

# ISA — Keycloak/OpenID POC Security Hardening

## Problem

The POC demonstrates securing a Spring Boot + Angular stack with Keycloak (OIDC).
A read of the security-critical surface surfaced concrete weaknesses:

- **F1 (correctness + security, HIGH):** `app-springboot/application.yml` validates incoming
  JWTs against realm `mba-cdsd-app` (resource-server `issuer-uri`, line 45) while the OAuth2
  login flow, the realm export, and the downstream service all use realm `bzhcamp`.
  `mba-cdsd-app` appears nowhere else in the repo — an orphan. Any bearer token the app
  receives would be rejected (wrong issuer), and the dual-role design described in the README
  is silently broken.
- **F2 (hardening, HIGH):** Neither resource server validates the JWT `aud` (audience) claim.
  Both rely on `issuer-uri` defaults (signature + issuer + expiry only). A token minted for
  one client can be replayed against another service in the same realm — the classic
  Keycloak/Spring "confused deputy" gap.
- **F3 (robustness, MEDIUM):** `KeycloakJwtRolesConverter` (service) calls
  `resourceClaims.get("roles").forEach(...)` with no null guard — a malformed `resource_access`
  entry without a `roles` key throws NPE → HTTP 500 instead of a clean 401/empty authorities.
- **F4 (hardening, MEDIUM):** `app-springboot` SecurityConfig permits all single-segment root
  paths via `AntPathRequestMatcher("/*").permitAll()` — broader than the actual public surface
  (`/`, `/about`, `/error`, `/favicon.ico`). Not deny-by-default. Deferred — needs runtime asset
  verification before tightening (could break FreeMarker static assets).

## Vision

A teaching POC whose security configuration is *correct by default* and *demonstrates the
recommended hardening controls* (audience validation, deny-by-default, defensive claim parsing)
— so a student reading it learns the right patterns, not the gaps. Euphoric surprise: the realm
bug — invisible until a bearer-token call 401s mysteriously — is found and explained, and the
audience-validation control is shown the idiomatic Spring way without breaking the running demo.

## Out of Scope

- Re-architecting the realm/clients or the Angular app.
- Enabling controls that break the running demo by default (audience validation ships opt-in).
- CI/CD, test-coverage, or pedagogical-docs work (other loop dimensions, not this one).
- Upgrading Spring Boot / Keycloak versions.

## Constraints

- Java 17+ / Spring Boot + Spring Security idioms only; no new heavy dependencies.
- Changes must keep `mvn compile` (and existing tests) green.
- No control may break the running demo when left at its default configuration.
- `bzhcamp` is the canonical realm; do not invent new realm/client names.

## Goal

Iteration 1: eliminate the confirmed correctness/security defects (F1 realm, F3 NPE) and add the
idiomatic, non-breaking audience-validation control (F2) to the pure resource server, verified by
a clean compile. Catalogue remaining hardening (F4 and below) as reviewed backlog for iteration 2.

## Criteria

- [ ] ISC-1: `app-springboot/application.yml` resource-server `issuer-uri` resolves to `.../realms/bzhcamp`.
- [ ] ISC-2: String `mba-cdsd-app` is absent from the entire repo (grep returns no match outside node_modules).
- [ ] ISC-3: `KeycloakJwtRolesConverter.convert` guards a null/missing `roles` list inside each `resource_access` entry (no unguarded `.get("roles").forEach`).
- [ ] ISC-4: A new `AudienceValidator implements OAuth2TokenValidator<Jwt>` exists in the service module.
- [ ] ISC-5: The validator rejects a JWT whose `aud` does not contain the expected audience and accepts one that does.
- [ ] ISC-6: Audience validation is opt-in — inactive when `keycloak.audience` is unset/blank, so default demo behavior is unchanged.
- [ ] ISC-7: A `JwtDecoder` bean wires default-with-issuer validators + the audience validator when the property is set.
- [ ] ISC-8: `service-springboot-rest` `mvn -q -DskipTests compile` exits 0.
- [ ] ISC-9: `app-springboot` `mvn -q -DskipTests compile` exits 0.
- [ ] ISC-10: Existing service tests still compile (`mvn -q test-compile` exits 0).
- [ ] ISC-11: Anti: no change alters default runtime behavior of the demo (audience off by default, no route newly blocked).
- [ ] ISC-12: Anti: no client secret, token, or credential is hardcoded by any change.
- [ ] ISC-13: A STRIDE-style threat read of the token flow is recorded to ground the findings.
- [ ] ISC-14: Remaining hardening (F4 `/*`, cast safety, CSRF posture) is written to `## Decisions` as iteration-2 backlog.

## Test Strategy

| isc | type | check | threshold | tool |
|-----|------|-------|-----------|------|
| ISC-1 | config | read-back issuer-uri | == bzhcamp | Read |
| ISC-2 | regression | grep orphan realm | 0 matches | Bash/grep |
| ISC-3 | code | grep guarded access | null-check present | Read/Grep |
| ISC-4 | code | file exists + implements interface | present | Read |
| ISC-5 | logic | reason over validator branches | reject/accept correct | inspection |
| ISC-6 | config | property default blank → skip | inactive | Read |
| ISC-7 | code | JwtDecoder bean present | wired | Read |
| ISC-8 | build | mvn compile service | exit 0 | Bash |
| ISC-9 | build | mvn compile app | exit 0 | Bash |
| ISC-10 | build | mvn test-compile | exit 0 | Bash |
| ISC-11 | anti | diff review | no default change | inspection |
| ISC-12 | anti | grep for secrets | none added | Grep |
| ISC-13 | analysis | Fabric STRIDE pattern | recorded | Skill(Fabric) |
| ISC-14 | doc | backlog written | present | Read |

## Features

| name | satisfies | depends_on | parallelizable |
|------|-----------|------------|----------------|
| fix-realm | ISC-1,2 | - | yes |
| fix-npe | ISC-3 | - | yes |
| add-audience-validator | ISC-4,5,6,7 | - | yes |
| verify-build | ISC-8,9,10 | fix-realm,fix-npe,add-audience-validator | no |
| threat-model | ISC-13 | - | yes |
| backlog | ISC-14 | threat-model | no |

## Decisions

## Changelog

## Verification