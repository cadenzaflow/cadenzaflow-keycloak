package org.cadenzaflow.bpm.examples.boot4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.List;
import org.cadenzaflow.bpm.engine.AuthorizationService;
import org.cadenzaflow.bpm.engine.IdentityService;
import org.cadenzaflow.bpm.engine.identity.Group;
import org.cadenzaflow.bpm.engine.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots this application - Spring Boot 4, the CadenzaFlow starter-4 and the
 * Keycloak plugin, all resolved as published artifacts - against a real
 * Keycloak, and checks that identity federation actually works.
 *
 * <p>This is the customer scenario end to end. It fails if any of these
 * regress: the plugin on Spring Framework 7 (the spring-6 jar dies here with
 * IncompatibleClassChangeError), the published POM chains of the -4 starters,
 * or the admin-group bootstrap.
 */
@SpringBootTest
@Testcontainers
class KeycloakFederationIT {

  @Container
  static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.5.6")
      .withRealmImportFile("cadenzaflow-realm.json");

  @DynamicPropertySource
  static void keycloakProperties(DynamicPropertyRegistry registry) {
    String realm = KEYCLOAK.getAuthServerUrl() + "/realms/cadenzaflow";
    String admin = KEYCLOAK.getAuthServerUrl() + "/admin/realms/cadenzaflow";
    registry.add("plugin.identity.keycloak.keycloak-issuer-url", () -> realm);
    registry.add("plugin.identity.keycloak.keycloak-admin-url", () -> admin);
  }

  @Autowired
  private IdentityService identityService;

  @Autowired
  private AuthorizationService authorizationService;

  @Test
  void usersAndGroupsComeFromKeycloak() {
    // the engine database holds no users - these are live Keycloak reads
    User kermit = identityService.createUserQuery().userId("kermit").singleResult();
    assertNotNull(kermit, "kermit should be federated from Keycloak");
    assertEquals("kermit@example.com", kermit.getEmail());

    List<Group> groups = identityService.createGroupQuery().groupMember("kermit").list();
    assertEquals(1, groups.size());
    assertEquals("cadenzaflow-admin", groups.get(0).getName());

    // control: a different user resolves to a different group
    List<Group> gonzoGroups = identityService.createGroupQuery().groupMember("gonzo").list();
    assertEquals(1, gonzoGroups.size());
    assertEquals("accounting", gonzoGroups.get(0).getName());
  }

  @Test
  void adminGroupIsBootstrapped() {
    // with authorization enabled the plugin seeds ADMIN grants for the
    // configured administratorGroupName at engine build
    long grants = authorizationService.createAuthorizationQuery().count();
    assertTrue(grants > 0, "the admin group should have seeded authorizations");
  }

}
