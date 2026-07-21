# Single CadenzaFlow image: Keycloak SSO webapps + token-claim RBAC on
# /engine-rest, in ONE instance.
#
# Stage 1 builds the tiny sso-chain glue module (the platform's oauth2 login
# chain, scoped to the webapp surface so it can coexist with the RBAC chain).
# Stage 2 layers the official Run image with the identity plugin, the OpenTMF
# RBAC gate, the glue jar and the configuration.

ARG RUN_VERSION=1.2.0

FROM maven:3.9-eclipse-temurin-17 AS glue
COPY sso-chain /build
WORKDIR /build
RUN mvn -B -q package

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

# --- token-claim RBAC gate (OpenTMF, Maven Central) ---
ADD ${CENTRAL}/org/opentmf/security/openid-rbac-security/${RBAC_VERSION}/openid-rbac-security-${RBAC_VERSION}.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/org/springframework/security/spring-security-oauth2-resource-server/6.5.10/spring-security-oauth2-resource-server-6.5.10.jar /opt/cadenzaflow/configuration/userlib/

# --- scoped SSO login chain (built in stage 1) ---
COPY --from=glue /build/target/scoped-sso-chain-1.0.0.jar /opt/cadenzaflow/configuration/userlib/

RUN chmod a+r /opt/cadenzaflow/configuration/userlib/*.jar

# --- configuration (SSO + RBAC in one file) ---
COPY config/default.yml /opt/cadenzaflow/configuration/default.yml

CMD ["/opt/cadenzaflow/internal/run.sh", "start", "--webapps", "--rest", "--oauth2"]
