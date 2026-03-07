# -------- Build (optimisation Quarkus) --------
FROM quay.io/keycloak/keycloak:25.0.0 as builder
ENV KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_DB=postgres \
    KC_CACHE=ispn \
    KC_FEATURES=passkeys
RUN /opt/keycloak/bin/kc.sh build

# (facultatif) couche UBI utilitaires
FROM registry.access.redhat.com/ubi9 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs && \
    dnf install --installroot /mnt/rootfs jq curl \
      --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all

# -------- Runtime --------
FROM quay.io/keycloak/keycloak:25.0.0

# récupère l’optimisation Quarkus + outils
COPY --from=builder /opt/keycloak/lib/quarkus/ /opt/keycloak/lib/quarkus/
COPY --from=ubi-micro-build /mnt/rootfs /

# <-- place ton export ici côté hôte: keycloak/import/realm-export.json -->
COPY keycloak/import/realm-export.json /opt/keycloak/data/import/realm-export.json
# (optionnel) bake aussi le thème si tu ne veux pas de volume:
# COPY themes/bzhcamp/ /opt/keycloak/themes/bzhcamp/

# droits pour l’utilisateur keycloak (UID 1000)
USER root
RUN chown 1000:0 /opt/keycloak/data/import/realm-export.json && \
    chmod 0644 /opt/keycloak/data/import/realm-export.json
USER 1000

WORKDIR /opt/keycloak

# Garde ENTRYPOINT "kc.sh" et passe les options en CMD (plus flexible)
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm", "--proxy-headers", "xforwarded",
     "--spi-theme-static-max-age=-1", "--spi-theme-cache-themes=false", "--spi-theme-cache-templates=false"]
