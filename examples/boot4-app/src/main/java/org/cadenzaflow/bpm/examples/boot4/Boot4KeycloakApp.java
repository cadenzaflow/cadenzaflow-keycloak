package org.cadenzaflow.bpm.examples.boot4;

import org.cadenzaflow.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Embedded CadenzaFlow on Spring Boot 4 with Keycloak as the identity source.
 *
 * <p>The engine itself is auto-configured by the starter; the only wiring this
 * application does is publishing the identity plugin as a bean. Its settings
 * are bound from {@code plugin.identity.keycloak.*} in application.yaml, which
 * is the same configuration surface used by the Run distribution.
 */
@SpringBootApplication
public class Boot4KeycloakApp {

  @Bean
  @ConfigurationProperties(prefix = "plugin.identity.keycloak")
  public KeycloakIdentityProviderPlugin keycloakIdentityProviderPlugin() {
    return new KeycloakIdentityProviderPlugin();
  }

  public static void main(String[] args) {
    SpringApplication.run(Boot4KeycloakApp.class, args);
  }

}
