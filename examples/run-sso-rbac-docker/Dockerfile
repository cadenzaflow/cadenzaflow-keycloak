# Shared image for both nodes of the SSO + token-RBAC example.
# Layered on the official Run image: identity plugin (CadenzaFlow Nexus),
# its runtime deps, the OpenTMF RBAC gate + resource-server jar, and the
# PostgreSQL driver (the two nodes share one engine database).
# The node's ROLE comes from the mounted configuration file and the start
# command (docker-compose.yml) - the image itself is role-neutral.

ARG RUN_VERSION=1.2.0
FROM cadenzaflow/cadenzaflow-bpm-platform:run-${RUN_VERSION}

ARG PLUGIN_VERSION=1.0.0
ARG RBAC_VERSION=1.1.1
ARG NEXUS=https://nexus.cadenzaflow.com/repository/cadenzaflow-nexus
ARG CENTRAL=https://repo1.maven.org/maven2

# --- identity plugin + runtime deps ---
ADD ${NEXUS}/org/cadenzaflow/bpm/extension/cadenzaflow-keycloak/${PLUGIN_VERSION}/cadenzaflow-keycloak-${PLUGIN_VERSION}.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/org/apache/httpcomponents/client5/httpclient5/5.4.1/httpclient5-5.4.1.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/org/apache/httpcomponents/core5/httpcore5/5.3.4/httpcore5-5.3.4.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/org/apache/httpcomponents/core5/httpcore5-h2/5.3.4/httpcore5-h2-5.3.4.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/com/github/ben-manes/caffeine/caffeine/3.1.8/caffeine-3.1.8.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/commons-codec/commons-codec/1.17.1/commons-codec-1.17.1.jar /opt/cadenzaflow/configuration/userlib/

# --- token-claim RBAC gate for /engine-rest (OpenTMF, Maven Central) ---
ADD ${CENTRAL}/org/opentmf/security/openid-rbac-security/${RBAC_VERSION}/openid-rbac-security-${RBAC_VERSION}.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/org/springframework/security/spring-security-oauth2-resource-server/6.5.10/spring-security-oauth2-resource-server-6.5.10.jar /opt/cadenzaflow/configuration/userlib/

# --- PostgreSQL driver (shared engine database) ---
ADD ${CENTRAL}/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar /opt/cadenzaflow/configuration/userlib/

RUN chmod a+r /opt/cadenzaflow/configuration/userlib/*.jar

# Default command (webapp node); docker-compose.yml overrides per node.
CMD ["/opt/cadenzaflow/internal/run.sh", "start", "--webapps", "--rest", "--oauth2"]
