# CadenzaFlow Run + Keycloak identity plugin, as a buildable image.
# Layers: official Run distribution (downloads.cadenzaflow.com)
#       + cadenzaflow-keycloak plugin (CadenzaFlow Nexus)
#       + its runtime dependencies (Maven Central)
#       + the SSO configuration from ./config

FROM eclipse-temurin:17-jre

ARG RUN_VERSION=1.2.0
ARG RUN_SERIES=1.2
ARG PLUGIN_VERSION=1.0.0
ARG DOWNLOADS=https://downloads.cadenzaflow.com/release/communityEdition
ARG NEXUS=https://nexus.cadenzaflow.com/repository/cadenzaflow-nexus
ARG CENTRAL=https://repo1.maven.org/maven2

# --- CadenzaFlow Run distribution ---
ADD ${DOWNLOADS}/cadenzaflow-bpm/run/${RUN_SERIES}/cadenzaflow-bpm-run-${RUN_VERSION}.tar.gz /tmp/run.tar.gz
RUN mkdir -p /opt/run && tar -xzf /tmp/run.tar.gz -C /opt/run && rm /tmp/run.tar.gz

# --- identity plugin + runtime deps Run does not ship ---
# (spring-web 6 is already on Run's classpath)
ADD ${NEXUS}/org/cadenzaflow/bpm/extension/cadenzaflow-keycloak/${PLUGIN_VERSION}/cadenzaflow-keycloak-${PLUGIN_VERSION}.jar /opt/run/configuration/userlib/
ADD ${CENTRAL}/org/apache/httpcomponents/client5/httpclient5/5.4.1/httpclient5-5.4.1.jar /opt/run/configuration/userlib/
ADD ${CENTRAL}/org/apache/httpcomponents/core5/httpcore5/5.3.4/httpcore5-5.3.4.jar /opt/run/configuration/userlib/
ADD ${CENTRAL}/org/apache/httpcomponents/core5/httpcore5-h2/5.3.4/httpcore5-h2-5.3.4.jar /opt/run/configuration/userlib/
ADD ${CENTRAL}/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar /opt/run/configuration/userlib/
ADD ${CENTRAL}/com/github/ben-manes/caffeine/caffeine/3.1.8/caffeine-3.1.8.jar /opt/run/configuration/userlib/
ADD ${CENTRAL}/commons-codec/commons-codec/1.17.1/commons-codec-1.17.1.jar /opt/run/configuration/userlib/
RUN chmod a+r /opt/run/configuration/userlib/*.jar

# --- SSO configuration ---
COPY config/default.yml /opt/run/configuration/default.yml

EXPOSE 8080
WORKDIR /opt/run
# --oauth2 puts the Spring Security oauth2 module on the classpath;
# it activates because config/default.yml defines a client registration.
CMD ["bash", "./internal/run.sh", "start", "--webapps", "--rest", "--oauth2"]
