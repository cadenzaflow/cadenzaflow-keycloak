# CadenzaFlow Run + Keycloak SSO, layered on the official Run image.
# Adds: the cadenzaflow-keycloak identity plugin (CadenzaFlow Nexus)
#     + its runtime dependencies Run does not ship (Maven Central)
#     + the validated SSO configuration from ./config
#     + the --oauth2 module flag (activates Spring Security oauth2)

ARG RUN_VERSION=1.2.0
FROM cadenzaflow/cadenzaflow-bpm-platform:run-${RUN_VERSION}

ARG PLUGIN_VERSION=1.0.0
ARG NEXUS=https://nexus.cadenzaflow.com/repository/cadenzaflow-nexus
ARG CENTRAL=https://repo1.maven.org/maven2

# --- identity plugin + runtime deps ---
# (spring-web 6 is already on Run's classpath)
ADD ${NEXUS}/org/cadenzaflow/bpm/extension/cadenzaflow-keycloak/${PLUGIN_VERSION}/cadenzaflow-keycloak-${PLUGIN_VERSION}.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/org/apache/httpcomponents/client5/httpclient5/5.4.1/httpclient5-5.4.1.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/org/apache/httpcomponents/core5/httpcore5/5.3.4/httpcore5-5.3.4.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/org/apache/httpcomponents/core5/httpcore5-h2/5.3.4/httpcore5-h2-5.3.4.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/com/github/ben-manes/caffeine/caffeine/3.1.8/caffeine-3.1.8.jar /opt/cadenzaflow/configuration/userlib/
ADD ${CENTRAL}/commons-codec/commons-codec/1.17.1/commons-codec-1.17.1.jar /opt/cadenzaflow/configuration/userlib/
RUN chmod a+r /opt/cadenzaflow/configuration/userlib/*.jar

# --- SSO configuration ---
COPY config/default.yml /opt/cadenzaflow/configuration/default.yml

# Same as the base image's CMD plus --oauth2; the oauth2 auto-configuration
# activates because config/default.yml defines a client registration.
CMD ["/opt/cadenzaflow/internal/run.sh", "start", "--webapps", "--rest", "--oauth2"]
