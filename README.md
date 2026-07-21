# cadenzaflow-keycloak

**CadenzaFlow Identity Provider Plugin for Keycloak** — lets the engine read
users and groups from Keycloak (read-only identity federation) so webapps and
REST share one identity source.

Provenance: fork of
[cibseven-keycloak](https://github.com/cibseven-community-hub/cibseven-keycloak)
(the maintained continuation of the EOL
[camunda-platform-7-keycloak](https://github.com/camunda-community-hub/camunda-platform-7-keycloak)),
re-namespaced to `org.cadenzaflow` and built against the CadenzaFlow engine.
Differences from upstream:

* **Two dependency lines, one codebase** (the CadenzaFlow engine supports
  JDK 11, upstream CIB seven is Java-17-only):

  | Artifact | HTTP stack | Java | Use in |
  |---|---|---|---|
  | `cadenzaflow-keycloak` | spring-web 6 / httpclient5 (upstream code untouched) | 17 | Run, Spring Boot starter, jakarta environments |
  | `cadenzaflow-keycloak-java11` | spring-web 5.3 / httpclient 4 | 11 | javax Tomcat distribution |

  The two differ in exactly ONE class (`KeycloakIdentityProviderFactory`,
  HTTP client wiring); all other sources are shared at build time.
* Carries only the core `extension` module. Upstream's `extension-jwt` is NOT
  included — CadenzaFlow's engine-rest ships its own framework-free Bearer
  provider (see the platform's engine-rest README).
* The upstream test suite runs against the primary module
  (`extension/src/test/`), wired to `cadenzaflow-bpm-spring-boot-starter-test`.
  It needs a running Keycloak — see "Maven test setup" below. The `-java11`
  module has no own tests (it differs in one class; the shared sources are
  covered by the primary suite).

## Installing on CadenzaFlow

### CadenzaFlow Run (validated end-to-end)

1. Copy the **primary** artifact and its dependencies that Run does not ship
   into `configuration/userlib/` (spring-web 6 is already on Run's classpath):

   ```
   cadenzaflow-keycloak-<version>.jar
   httpclient5-5.4.1.jar
   httpcore5-5.3.4.jar
   httpcore5-h2-5.3.4.jar
   gson-2.11.0.jar
   caffeine-3.1.8.jar
   commons-codec-1.17.1.jar
   ```

2. Register the plugin in `configuration/default.yml` (or `production.yml`):

   ```yaml
   cadenzaflow.bpm:
     run:
       process-engine-plugins:
         - plugin-class: org.cadenzaflow.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin
           plugin-parameters:
             keycloakIssuerUrl: https://<keycloak>/realms/<realm>
             keycloakAdminUrl:  https://<keycloak>/admin/realms/<realm>
             clientId: <client>
             clientSecret: <secret>
             useUsernameAsCamundaUserId: true
             administratorGroupName: cadenzaflow-admin
   ```

3. Name the Keycloak admin group **`cadenzaflow-admin`** if you can: that
   exact name is the engine's built-in admin group constant, so the plugin
   types it SYSTEM even on Keycloak endpoints that return brief
   representations (where a `type` group attribute is invisible). Any other
   name still works via `administratorGroupName`.

4. If you enable engine authorization (so the plugin seeds ADMIN
   authorization rows for the group), use the **first-class property**:

   ```yaml
   cadenzaflow.bpm.authorization.enabled: true
   ```

   Do NOT use `generic-properties.properties.authorization-enabled` — the
   starter's `DefaultAuthorizationConfiguration` runs later and silently
   overwrites it with the default (`false`).

### Apache Tomcat (javax distribution)

Use the **`cadenzaflow-keycloak-java11`** artifact. Copy into the Tomcat
distribution's `lib/` folder:

```
cadenzaflow-keycloak-java11-<version>.jar
spring-web-5.3.39.jar
spring-core-5.3.39.jar
spring-beans-5.3.39.jar
spring-jcl-5.3.39.jar
httpclient-4.5.14.jar
httpcore-4.4.16.jar
gson-2.11.0.jar
caffeine-3.1.8.jar
commons-codec-1.17.1.jar
```

and register the plugin in `conf/bpm-platform.xml`:

```xml
<plugin>
  <class>org.cadenzaflow.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin</class>
  <properties>
    <property name="keycloakIssuerUrl">https://&lt;keycloak&gt;/realms/&lt;realm&gt;</property>
    <property name="keycloakAdminUrl">https://&lt;keycloak&gt;/admin/realms/&lt;realm&gt;</property>
    <property name="clientId">&lt;client&gt;</property>
    <property name="clientSecret">&lt;secret&gt;</property>
    <property name="useUsernameAsCamundaUserId">true</property>
    <property name="administratorGroupName">cadenzaflow-admin</property>
  </properties>
</plugin>
```

Note: the Run path above is validated against a live Keycloak (identity
federation, login redirect, admin authorization seeding). The Tomcat path
compiles and shares all but one class with the validated line, but has not
been exercised end-to-end yet.

---

## Quickstart: CadenzaFlow Run + Keycloak SSO, end to end

Goal: log into the CadenzaFlow webapps with a Keycloak user; users and groups
come from Keycloak (nothing is created in the engine DB). Validated against
Keycloak 26 and CadenzaFlow Run 1.2.0.

> How authentication, identity federation and authorization fit together —
> including why **Keycloak groups (not token roles) drive permissions** and
> how machine clients fit in — is explained in detail in
> [doc/identity-and-authorization.md](doc/identity-and-authorization.md).

### Step 1 — Keycloak

1. Create a realm (example below uses `cadenzaflow`).
2. Create a client `cadenzaflow-webapps`:
   * Client authentication **ON** (confidential) — note the generated
     **client secret** (Credentials tab)
   * Standard flow (authorization code) enabled
   * Service accounts roles **ON**
   * Valid redirect URIs: `https://<run-host>/login/oauth2/code/keycloak`
     (add the same value as a valid *post logout* redirect URI)
   * Screenshots and Keycloak-version footnotes: see
     "Prerequisites in your Keycloak realm" below.
3. On the client's *Service accounts roles* tab assign the
   `realm-management` roles `query-groups`, `query-users`, `view-users`
   (this is what lets the plugin read users/groups).
4. Create a group named **`cadenzaflow-admin`** and add your admin user to
   it. Use that exact name if you can — it is the engine's built-in admin
   group constant, so it is auto-typed SYSTEM everywhere.
5. **If the browser and Run reach Keycloak via different URLs** (typical
   with containers: browser uses a published URL, Run uses an internal
   one), pin the issuer or logins fail with
   `invalid_user_info_response`:

   ```
   KC_HOSTNAME: <browser-facing URL, e.g. https://keycloak.example.com>
   KC_HOSTNAME_BACKCHANNEL_DYNAMIC: "true"
   ```

### Step 2 — CadenzaFlow Run

1. Put the plugin jars into `configuration/userlib/` — see
   "Installing on CadenzaFlow" above.
2. Merge this into `configuration/default.yml` (single Keycloak URL,
   reachable by both browser and Run):

   ```yaml
   cadenzaflow.bpm:
     # no admin-user block: identity is read-only via Keycloak;
     # admin comes from administratorGroupName below
     oauth2:
       identity-provider:
         enabled: false          # groups come from the engine plugin, not the OAuth2 principal
     authorization:
       enabled: true             # optional; NEVER via generic-properties (silently overwritten)
     run:
       process-engine-plugins:
         - plugin-class: org.cadenzaflow.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin
           plugin-parameters:
             keycloakIssuerUrl: https://keycloak.example.com/realms/cadenzaflow
             keycloakAdminUrl:  https://keycloak.example.com/admin/realms/cadenzaflow
             clientId: cadenzaflow-webapps
             clientSecret: <secret>
             useUsernameAsCamundaUserId: true
             administratorGroupName: cadenzaflow-admin

   spring.security.oauth2.client:
     registration:
       keycloak:
         provider: keycloak
         client-id: cadenzaflow-webapps
         client-secret: <secret>
         scope: openid,profile,email
         authorization-grant-type: authorization_code
     provider:
       keycloak:
         issuer-uri: https://keycloak.example.com/realms/cadenzaflow
         user-name-attribute: preferred_username   # must match useUsernameAsCamundaUserId
   ```

   *Different URLs for browser vs Run?* Drop `issuer-uri`, set the four
   endpoints (`authorization-uri` browser-facing; `token-uri`,
   `jwk-set-uri`, `user-info-uri` internal) and set
   `redirect-uri: "{baseUrl}/login/oauth2/code/keycloak"` explicitly —
   plus the `KC_HOSTNAME` pin from step 1.5.

3. Start Run with the oauth2 module:

   ```
   start.sh --webapps --rest --oauth2
   ```

### Step 3 — Verify

| Check | Expected |
|---|---|
| Open `https://<run>/cadenzaflow/app/cockpit/default/` | Redirect to the Keycloak login page |
| Log in with the `cadenzaflow-admin` user | Back to Cockpit, dashboard renders; Admin webapp accessible |
| `GET /engine-rest/group?member=<username>` | The user's Keycloak groups (engine DB stays empty) |
| Boot log | `KEYCLOAK-01001 PLUGIN KeycloakIdentityProviderPlugin activated` and, with authorization on, one `KEYCLOAK-01002 GRANT group ...` line per resource |

Common failures:

| Symptom | Cause / fix |
|---|---|
| Login page shows, then bounces to `/login?error` | userinfo 401 — split-URL issuer mismatch → step 1.5 (`KC_HOSTNAME`) |
| No authorization rows although enabled | flag set via `generic-properties` → use `cadenzaflow.bpm.authorization.enabled` |
| Users/groups empty in Cockpit | service-account roles missing (step 1.3) or wrong `keycloakAdminUrl` |
| Engine-rest with OIDC instead of Basic | that is a platform feature, not this plugin — see the `engine-rest` README (OIDC Bearer section) in cadenzaflow-bpm-platform |

---

The configuration reference below is inherited from upstream and still applies
(property names are unchanged — including the `...AsCamundaUserId` names);
"CIB seven" in the text reads as "CadenzaFlow" for our purposes. Ignore
sections about modules this fork does not carry.

---

# Keycloak Identity Provider Plugin — reference (from upstream cibseven-keycloak)
[![Apache License V.2](https://img.shields.io/badge/license-Apache%20V.2-blue.svg)](./LICENSE)

Artifacts are published to the CadenzaFlow Nexus
(`https://nexus.cadenzaflow.com/repository/cadenzaflow-nexus`,
groupId `org.cadenzaflow.bpm.extension`) — not to Maven Central.

![Keycloak](doc/keycloak.png "https://www.keycloak.org/") 

Keycloak&trade; (<https://www.keycloak.org/>) is an Open Source Identity and Access Management platform including advanced features such as User Federation, Identity Brokering and Social Login.

CIB seven&trade; (<https://cibseven.org>) is perfectly suited to carry out BPM projects in the cloud. Identity management in the cloud, however, often differs from classical approaches. CIB seven already provides a generic sample for Single Sign On when using Spring Boot. See <https://github.com/camunda-consulting/code/tree/master/snippets/springboot-security-sso>.
Specific instructions on how to use Spring Boots OAuth2 SSO in combination with this Keycloak Identity Provider Plugin can be found below.

**Why this plugin?** SSO is sufficient in case you only want authentication but have no further advanced security roles. If one needs to use  IdentityService APIs of CIB seven or wants to see actual Users and Groups show up in Cockpit, a custom IdentityProvider needs to be implemented as well.

This plugin provides the basis for using Keycloak as Identity Management solution and will provide a ReadOnlyIdentityProvider. What you will get is a fully integrated solution for using Keycloak as an Identity Provider in CIB seven receiving users and groups from Keycloak. The authorization of these users and groups for CIB seven resources itself remains within CIB seven. This plugin allows the usage of Keycloak as Identity Provider even without SSO.
  
**Beware: in case you want to use Keycloak's advanced login capabilities for social connections you must configure SSO as well.**
Password grant exchanges are only supported for Keycloak's internally managed users and users of an LDAP / Keberos User federation. Hence without SSO you will only be able to login with users managed by such connections.

Fork version: `1.0.0` (upstream base: cibseven-keycloak `2.1.0`)<br >
Latest tests with: Keycloak `26.5.6`, CadenzaFlow `1.2.0` (131 tests in CI)

#### Features
Changes in version `2.0.0`

* Initial Version

Known limitations:

*   A strategy to distinguish SYSTEM and WORKFLOW groups is missing. Currently only the administrator group is mapped to type SYSTEM.
*   Some query filters are applied on the client side - the Keycloak REST API does not allow full criteria search in all required cases.
*   Sort criteria for queries are implemented on the client side - the Keycloak REST API does not allow result ordering.
*   Tenants are currently not supported.

## Prerequisites in your Keycloak realm

1. Keycloak docker images can be found on [Keycloak Docker Hub](https://hub.docker.com/r/keycloak/keycloak "Keycloak Docker Images").
2. Create a new client named `camunda-identity-service` with access type confidential and service accounts enabled:
    ![IdentityServiceSettings](doc/identity-service_settings.png "Identity Service Settings")
   Please be aware, that beginning with Keycloak 18, you do not only have to configure a valid redirect URL, but
   a valid post logout redirect URL as well. To keep things easy values can be the same.
3. Since Keycloak 20, user queries require an 'openid' scope for OIDC clients. To enable this, create an 'openid' scope under client scopes and add add this the `camunda-identity-service` client.
![openid-client-scope.png](doc/openid-clientscope.png "Client scopes") 
4. In order to use refresh tokens set the "Use Refresh Tokens For Client Credentials Grant" option within the "OpenID Connect Compatibility Modes" section (available in newer Keycloak versions):

    ![IdentityServiceOptions](doc/identity-service_options.png "Identity Service Options")
5. Add the roles `query-groups, query-users, view-users` to the service account client roles of your realm (choose `realm-management` or `master-realm`, depending on whether you are using a separate realm or master):
    ![IdentityServiceRoles](doc/identity-service_roles.png "Identity Service Roles")
6. Your client credentials can be found here:
    ![IdentityServiceCredentials](doc/identity-service_credentials.png "Identity Service Credentials")
7. Once you're done with the basic setup you're now ready to manage your users and groups with Keycloak. Please keep in mind, that in order to make the Keycloak Identity Provider work, you will need at least one dedicated CIB seven admin group or CIB seven user in your realm. Whether you create this group/user manually or import it using the LDAP user federation or any other Identity Provider is up to you.
    ![KeycloakGroups](doc/keycloak-groups.png "Keycloak Realm Groups")

## Usage with CIB seven Spring Boot

Maven Dependencies:
```xml
<dependency>
    <groupId>org.cadenzaflow.bpm.extension</groupId>
    <artifactId>cadenzaflow-keycloak</artifactId>
    <version>2.1.0</version>
</dependency>
```

Add the following class to your CIB seven Spring Boot application in order to activate the Keycloak Identity Provider Plugin:

```java
package <your-package>;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.cadenzaflow.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;

@Component
@ConfigurationProperties(prefix="plugin.identity.keycloak")
public class KeycloakIdentityProvider extends KeycloakIdentityProviderPlugin {
}
```

Configuration in `application.yaml` will then look as follows:

```yml
camunda.bpm:
  ...
  authorization:
    enabled: true

plugin.identity.keycloak:
  keycloakIssuerUrl: https://<your-keycloak-server>/auth/realms/<realm-name>
  keycloakAdminUrl: https://<your-keycloak-server>/auth/admin/realms/<realm-name>
  clientId: camunda-identity-service
  clientSecret: 42aa42bb-1234-4242-a24a-42a2b420cde0
  useEmailAsCamundaUserId: true
  administratorGroupName: camunda-admin
```

Hint: the engine must **not** create a user upon startup - the plugin is a *ReadOnly*IdentityProvider. Hence you must **not** configure an `admin-user` for `camunda.bpm` in your `application.yaml`. The following configuration will likely cause errors upon startup: 

```yml
camunda.bpm:
# DON'T DO THIS
  admin-user:
    id: demo
    password: demo
    firstName: Demo
```

The `admin-user` part must be deleted in order to work properly. The recommended procedure for creating the admin user and admin group in Keycloak is to have the deployment pipeline do this during the environment setup phase.
    
A list of configuration options can be found below:

| *Property*                        | *Description*                                                                                                                                                                                                                                                                                                                                                                                                                           |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `keycloakIssuerUrl`               | The basic issuer URL of your Keycloak server including the realm.<br />Sample for master realm: `https://<your-keycloak-server>/auth/realms/master`                                                                                                                                                                                                                                                                                     |
| `keycloakAdminUrl`                | The admin URL of the Keycloak server REST API including the realm.<br />Sample for master realm: `https://<your-keycloak-server>/auth/admin/realms/master`                                                                                                                                                                                                                                                                              |
| `clientId`                        | The Client ID of your application.                                                                                                                                                                                                                                                                                                                                                                                                      |
| `clientSecret`                    | The Client Secret of your application.                                                                                                                                                                                                                                                                                                                                                                                                  |
| `useEmailAsCamundaUserId`         | Whether to use the Keycloak email attribute as user ID. Default is `false`.<br /><br />This is option is a fallback in case you don't use SSO and want to login using web interface with your mail address and not the cryptic internal Keycloak ID. Keep in mind that you will only be able to login without SSO with Keycloak's internally managed users and users managed by the LDAP / Keberos User federation. |
| `useUsernameAsCamundaUserId`      | Whether to use the Keycloak username attribute as user ID. Default is `false`. In the default case the plugin will use the internal Keycloak ID as user ID.                                                                                                                                                                                                                                                         |
| `useGroupPathAsCamundaGroupId`    | Whether to use the Keycloak unique group path as group ID. Default is `false`. In the default case the plugin will use the internal Keycloak ID as group ID.<br />This flag is particularly useful in case you want to have human readable group IDs and recommended when using groups in authorization management.<br />*Since 1.1.0*                                                                    |
| `enforceSubgroupsInGroupQuery`    | Starting with Keycloak version 23 the group query without any other search parameters does not automatically return subgroups within the result. Set this flag to `true` in case you use subgroups together with Keycloak 23 or higher. Otherwise leave it to the default `false` and benefit from better performance.<br />*Since 7.21.1*                                                                                              |
| `administratorGroupName`          | The name of the administrator group. If this name is set and engine authorization is enabled, the plugin will create group-level Administrator authorizations on all built-in resources.                                                                                                                                                                                                                                                |
| `administratorUserId`             | The ID of the administrator user. If this ID is set and engine authorization is enabled, the plugin will create user-level Administrator authorizations on all built-in resources.                                                                                                                                                                                                                                                      |
| `authorizationCheckEnabled`       | If this property is set to true, then authorization checks are performed when querying for users or groups. Otherwise authorization checks are not performed when querying for users or groups. Default: `true`.<br />*Note*: If you have a huge amount of Keycloak users or groups we advise to set this property to false to improve the performance of the user and group query.                                                     |
| `maxResultSize`                   | Maximum result size of queries against the Keycloak API. Default: `250`.<br /><br />*Beware*: Setting the parameter to a too low value can lead to unexpected effects. Keep in mind that parts of the filtering takes place on the client side / within the plugin itself. Setting the parameter to a too high value can lead to performance and memory issues.<br />*Since 1.5.0*                                                      |
| `maxHttpConnections`              | Maximum number HTTP connections for the Keycloak connection pool. Default: `50`                                                                                                                                                                                                                                                                                                                                                         |
| `disableSSLCertificateValidation` | Whether to disable SSL certificate validation. Default: `false`. Useful in test environments.                                                                                                                                                                                                                                                                                                                                           |
| `truststore`                      | Optional file path to a truststore file. Default: `null`. In the default case the default Java truststore will be used.<br />*Since 7.21.3*                                                                                                                                                                                                                                                                                             |
| `truststorePassword`              | Optional password for the truststore. Default: `null`.<br />*Since 7.21.3*                                                                                                                                                                                                                                                                                                                                                              |
| `proxyUri`                        | Optional URI of a proxy to use. Default: `null`, example: `http://proxy:81`.<br />*Since 2.0.0*                                                                                                                                                                                                                                                                                                                                         |
| `proxyUser`                       | Optional username for proxy authentication. Default: `null`.<br />*Since 2.0.0*                                                                                                                                                                                                                                                                                                                                                         |
| `proxyPassword`                   | Optional password for proxy authentication. Default: `null`.<br />*Since 2.0.0*                                                                                                                                                                                                                                                                                                                                                         |
<!--
| `charset` | Charset to use for REST communication with Keycloak Server. Default: `UTF-8`.<br />*Since 1.1.0* |
-->

## Caching options

This is a ReadOnlyIdentityProvider which translates all queries against the CIB seven IdentityService in REST queries against Keycloak. Under high load it makes sense to not request the same things again and again, especially since the data of users and groups do not change every second. Therefore this plugin provides an optional cache feature.

### User and group query caching

In order to activate caching of user and group queries you have the following options available:

| *Property* | *Description* |
| --- | --- |
| `cacheEnabled` | Enable caching of user and group queries to Keycloak to improve performance. Default: `false`.<br />*Since 2.2.0* |
| `maxCacheSize` | Maximum size of the cache. Least used entries are evicted when this limit is reached. Default: `500`.<br />*Since 2.2.0* |
| `cacheExpirationTimeoutMin` | Time (in minutes) after which a cached entry is evicted. Default: `15 minutes`.<br />*Since 2.2.0* |

Besides caching of user and group queries there is another scenario where caching could make sense. 

### Login caching

Imagine a setup with lots of External Task Clients using HTTP Basic Auth against the CIB seven REST API (e.g. set `camunda.bpm.run.auth.enabled: true` when using CIB seven Run). Your External Task Clients then might trigger the IdentityProvider's `checkPassword` function at high frequency. This function requests a token from Keycloak each time it is called. In case of a successful response the login is treated as valid. High frequency then means requesting lots of tokens - in the worst case all for the same user and before an already delivered token has timed out. Therefore this plugin provides an optional login cache feature as well.

In order to activate the login cache you have the following options available:

| *Property* | *Description* |
| --- | --- |
 `loginCacheEnabled` | Enable caching of login / check password requests to Keycloak to improve performance. Not applicable in case of SSO scenarios, but useful e.g. in case of External Tasks clients using HTTP Basic Auth only. Default: `false` <br />*Since 2.2.3* |
| `loginCacheSize` | Maximum size of the login cache. Least used entries are evicted when this limit is reached. Default: `50`.<br />*Since 2.2.3* |
| `loginCacheExpirationTimeoutMin` | Time (in minutes) after which a login cache entry is evicted. Default: `15 minutes`.<br />*Since 2.2.3* |

On the downside this feature bypasses the password grant exchange function of Keycloak until the configured timeout expires. So the choice is yours. Please be aware that the login cache is not applicable for SSO scenarios.

## Activating Single Sign On for CIB seven webapp

The CIB seven webclient manages SSO by its own, so we only need to configure the application yaml like follows:

```yml
  cibseven.webclient:
    user:
      provider: org.cadenzaflow.webapp.auth.KeycloakUserProvider # 1
    sso: # 2
      active: true
      endpoints:
        authorization: <authUri>
        token: <tokenUri>
        jwks: <certsUri>
        user: <usersUri>
      clientId: <clientId>
      clientSecret: <clientSecret>
      scopes: openid email profile # 3
      userIdProperty: preferred_username
      userNameProperty: name
```
1. Using KeycloakUserProvider.
2. SSO login enabled for CIB seven webclient.
3. Defines the openid, profile and email scopes.


## Activating Single Sign On for the legacy Camunda webapp

In this part, we’ll discuss how to activate SSO – Single Sign On – for the CIB seven Web App using Spring Boot and Spring Security 5.2.x OAuth 2.0 Client capabilities in combination with this plugin and Keycloak as authorization server.

In order to setup Spring Boot's OAuth2 security add the following Maven dependencies to your project:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

What we need is a bridge between Spring Security and CIB seven. Hence insert a KeycloakAuthenticationProvider as follows:

```java
/**
  * OAuth2 Authentication Provider for usage with Keycloak and KeycloakIdentityProviderPlugin. 
  */
public class KeycloakAuthenticationProvider extends ContainerBasedAuthenticationProvider {

    @Override
    public AuthenticationResult extractAuthenticatedUser(HttpServletRequest request, ProcessEngine engine) {

        // Extract user-name-attribute of the OAuth2 token
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken) || !(authentication.getPrincipal() instanceof OidcUser)) {
            return AuthenticationResult.unsuccessful();
        }
        String userId = ((OidcUser)authentication.getPrincipal()).getName();
        if (StringUtils.isEmpty(userId)) {
            return AuthenticationResult.unsuccessful();
        }

        // Authentication successful
        AuthenticationResult authenticationResult = new AuthenticationResult(userId, true);
        authenticationResult.setGroups(getUserGroups(userId, engine));

        return authenticationResult;
    }

    private List<String> getUserGroups(String userId, ProcessEngine engine){
        List<String> groupIds = new ArrayList<>();
        // query groups using KeycloakIdentityProvider plugin
        engine.getIdentityService().createGroupQuery().groupMember(userId).list()
            .forEach( g -> groupIds.add(g.getId()));
        return groupIds;
    }

}
```

Last but not least add a security configuration and enable OAuth2 SSO for the legacy Camunda webapp:

```java
/**
 * Legacy Camunda Web application SSO configuration for usage with KeycloakIdentityProviderPlugin.
 */
@ConditionalOnMissingClass("org.springframework.test.context.junit.jupiter.SpringExtension")
@EnableWebSecurity
@Configuration
public class WebAppSecurityConfig {

	 @Inject
	 private KeycloakLogoutHandler keycloakLogoutHandler;
	
    private final String legacyWebappPath;

    public WebAppSecurityConfig(CamundaBpmProperties properties) {
      this.legacyWebappPath = properties.getWebapp().getLegacyApplicationPath();
    }
    
    @Bean
    @Order(2)
    public SecurityFilterChain httpSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(request -> {
                    String fullPath = request.getServletPath() + (request.getPathInfo() != null ? request.getPathInfo() : "");
                    return fullPath.startsWith(legacyWebappPath) || 
                       fullPath.startsWith(OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI) ||
                       fullPath.startsWith("/login") ||
                       fullPath.startsWith("/logout");
                })
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(antMatcher("/api/**"), antMatcher("/engine-rest/**")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                antMatcher(legacyWebappPath + "/assets/**"),
                                antMatcher(legacyWebappPath + "/app/**"),
                                antMatcher(legacyWebappPath + "/api/**"),
                                antMatcher(legacyWebappPath + "/lib/**"))
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .oauth2Login(withDefaults())
                .build();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Bean
    public FilterRegistrationBean containerBasedAuthenticationFilter(){

        FilterRegistrationBean filterRegistration = new FilterRegistrationBean();
        filterRegistration.setFilter(new ContainerBasedAuthenticationFilter());
        filterRegistration.setInitParameters(Collections.singletonMap("authentication-provider", "org.cadenzaflow.bpm.extension.keycloak.showcase.sso.KeycloakAuthenticationProvider"));
        filterRegistration.setOrder(201); // make sure the filter is registered after the Spring Security Filter Chain
        filterRegistration.addUrlPatterns(legacyWebappPath + "/app/*");
        return filterRegistration;
    }

    // The ForwardedHeaderFilter is required to correctly assemble the redirect URL for OAUth2 login. 
    // Without the filter, Spring generates an HTTP URL even though the container route is accessed through HTTPS.
    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new ForwardedHeaderFilter());
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filterRegistrationBean;
    }

    @Bean
    @Order(0)
    public RequestContextListener requestContextListener() {
        return new RequestContextListener();
    }
}
```

Finally configure Spring Security with your Keycloak Single Page Web App `client-id` and `client-secret` in `application.yaml` as follows:

```yml
# Spring Boot Security OAuth2 SSO
spring.security.oauth2:
  client:
    registration:
      keycloak:
        provider: keycloak
        client-id: camunda-identity-service
        client-secret: yyy2121abc21def2121ghi212132121abc21def2121ghi2121eyyy
        authorization-grant-type: authorization_code
        redirect-uri: "{baseUrl}/{action}/oauth2/code/{registrationId}"
        scope: openid, profile, email
    provider:
      keycloak:
        issuer-uri: https://<your-keycloak-server>/auth/realms/camunda
        authorization-uri: https://<your-keycloak-server>/auth/realms/camunda/protocol/openid-connect/auth
        user-info-uri: https://<your-keycloak-server>/auth/realms/camunda/protocol/openid-connect/userinfo
        token-uri: https://<your-keycloak-server>/auth/realms/camunda/protocol/openid-connect/token
        jwk-set-uri: https://<your-keycloak-server>/auth/realms/camunda/protocol/openid-connect/certs
        # set user-name-attribute one of: 
        # - sub                -> default; using keycloak ID as cibseven user ID
        # - email              -> useEmailAsCamundaUserId=true
        # - preferred_username -> useUsernameAsCamundaUserId=true
        user-name-attribute: email
```

**Beware**: You have to set the parameter ``user-name-attribute`` of the ``spring.security.oauth2.client.provider.keycloak`` in a way that it matches the configuration of your KeycloakIdentityProviderPlugin: 

* `useEmailAsCamundaUserId: true` - set `user-name-attribute: email`
* `useUsernameAsCamundaUserId: true` - set `user-name-attribute: preferred_username`
* neither of the above two, using Keycloak's ID as default - set `user-name-attribute: sub`

Keep in mind that Keycloak's `email` attribute might not always be unique, depending on your setup. Email uniqueness can be configured on a per realm level depending on the setting *Login with email*.

## Installation and examples

Installation on CadenzaFlow Run and Apache Tomcat is covered by
["Installing on CadenzaFlow"](#installing-on-cadenzaflow) and the
[Quickstart](#quickstart-cadenzaflow-run--keycloak-sso-end-to-end) at the top
of this README. A complete, buildable Docker/Compose SSO example — Run +
Keycloak + this plugin with a demo realm, one `docker compose up --build`
away — lives in [`examples/run-sso-docker/`](examples/run-sso-docker/); its
README also spells out the authorization model (token authenticates, Keycloak
*groups* authorize). A second example,
[`examples/run-sso-rbac-docker/`](examples/run-sso-rbac-docker/), adds a
token-claim RBAC gate in front of `engine-rest` (OpenTMF's
openid-rbac-security — the opentmf-camunda7 model) next to the SSO webapps. The upstream `examples/` walk-throughs
(Wildfly/Kubernetes etc.) are not carried by this fork; they live in the
[upstream repository](https://github.com/cibseven-community-hub/cibseven-keycloak).

## Unit testing the plugin

In order to run the unit tests I have used a local docker setup of Keycloak with `docker-compose.yml` as follows:

```docker-compose
version: "3.9"

services:
  jboss.keycloak:
    image: quay.io/keycloak/keycloak:26.4.5
    restart: unless-stopped
    environment:
      TZ: Europe/Berlin
      DB_VENDOR: h2
      KEYCLOAK_ADMIN: keycloak
      KEYCLOAK_ADMIN_PASSWORD: keycloak1!
      KC_HTTP_RELATIVE_PATH: /auth
    ports:
      - "8443:8443"
      - "8080:8080"
    command:
      - start-dev
```

For details see documentation on [Running Keycloak in a container](https://www.keycloak.org/server/containers "Running Keycloak in a container").

### Maven test setup

Running unit tests from Maven requires configuring the details of a running Keycloak server. This can be achieved by setting the following environment variables:

| *Environment Variable*                      | *Description*                                                                                                        |
|---------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `KEYCLOAK_URL`                              | Keycloak server URL.<br />Default value: `http://localhost:8080/auth`                                                |
| `KEYCLOAK_ADMIN_USER`                       | The admin user of the Keycloak server.<br />Default value: `keycloak`                                                |
| `KEYCLOAK_ADMIN_PASSWORD`                   | The admin password of the Keycloak server.<br />Default value: `keycloak1!`                                          |
| `KEYCLOAK_ENFORCE_SUBGROUPS_IN_GROUP_QUERY` | Wether to enforce subgroup results in group queries when testing with Keycloak >= `23.0.0`<br />Default value: `true`|

In case you choose Keycloak in the new Quarkus distribution, please be aware that `/auth` has been removed from the default context path.
Hence, it is required to change the `KEYCLOAK_URL` for the tests. Tests run successfully against the Quarkus
distribution, in case you start Keycloak in Development mode.

------------------------------------------------------------

That's it. Have a happy Keycloak experience and focus on what really matters: the core processes of your customer.

------------------------------------------------------------

## Resources

* [Issue Tracker](https://github.com/cibseven-community-hub/cadenzaflow-keycloak/issues)
* [Contributing](https://github.com/cibseven-community-hub/cadenzaflow-keycloak/blob/master/CONTRIBUTING.md)

## Acknowledgement

We would like to thank [Gunnar von der Beck](https://www.xing.com/profile/Gunnar_vonderBeck/portfolio) from
[Accso - Accelerated Solutions GmbH](https://accso.de/) for the idea and work on this plugin - 
this fork is based on [camunda-platform-7-keycloak](https://github.com/camunda-community-hub/camunda-platform-7-keycloak).

## License 

License: [Apache License 2.0](https://opensource.org/licenses/Apache-2.0)
