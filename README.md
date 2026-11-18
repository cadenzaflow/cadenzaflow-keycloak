# cadenzaflow-keycloak

**Keycloak identity provider plugin for CadenzaFlow.** The process engine
reads its users and groups from Keycloak, read-only. The webapps, the REST API
and the engine's authorizations then share one identity source, and nothing is
created in the engine database.

Single sign-on alone only tells the engine who logged in. The engine also has
to look users and groups up: to check authorizations, to resolve the candidate
groups of a task, to show the user and group lists in Admin. This plugin
answers those lookups from Keycloak. It also works without single sign-on:
the login form of the webapps then checks the password against Keycloak.

## Artifacts

Group `org.cadenzaflow.bpm.extension`, current release `1.1.2`, published on
Maven Central.

| Artifact | Built against | Java | Use in |
|---|---|---|---|
| `cadenzaflow-keycloak` | Spring Web 6, Apache HttpClient 5 | 17 | CadenzaFlow Run, Spring Boot 3 applications, Jakarta application servers |
| `cadenzaflow-keycloak-4` | Spring Web 7, Apache HttpClient 5 | 17 | Spring Boot 4 applications. The Spring Web 6 build fails there with `IncompatibleClassChangeError`. |
| `cadenzaflow-keycloak-java11` | Spring Web 5.3, Apache HttpClient 4 | 11 | the Tomcat distribution on `javax` |

The three artifacts are built from one code base. `cadenzaflow-keycloak-4` is
the same source compiled against Spring 7. `cadenzaflow-keycloak-java11`
differs in one class, the one that sets up the HTTP client.

## Installing on CadenzaFlow

### CadenzaFlow Run (validated end-to-end)

1. Copy the **primary** artifact and its dependencies that Run does not ship
   into `configuration/userlib/` (spring-web 6 is already on Run's classpath):

   ```
   cadenzaflow-keycloak-<version>.jar
   httpclient5-5.6.5.jar
   httpcore5-5.4.4.jar
   httpcore5-h2-5.4.4.jar
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
been exercised end-to-end yet. CadenzaFlow Run 4 and the Tomcat 11
distribution have not been tested with the plugin yet.

---

## Quickstart: CadenzaFlow Run + Keycloak SSO, end to end

Goal: log into the CadenzaFlow webapps with a Keycloak user; users and groups
come from Keycloak (nothing is created in the engine DB). Validated against
Keycloak 26 with CadenzaFlow Run 1.2.0 and 1.3.0.

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

## Spring Boot applications

Add the artifact that fits your Spring Boot version (see [Artifacts](#artifacts)):

```xml
<dependency>
  <groupId>org.cadenzaflow.bpm.extension</groupId>
  <artifactId>cadenzaflow-keycloak-4</artifactId>
  <version>1.1.2</version>
</dependency>
```

Register the plugin as a bean:

```java
@Bean
@ConfigurationProperties(prefix = "plugin.identity.keycloak")
public KeycloakIdentityProviderPlugin keycloakIdentityProviderPlugin() {
  return new KeycloakIdentityProviderPlugin();
}
```

And configure it in `application.yaml`:

```yaml
cadenzaflow.bpm:
  authorization:
    enabled: true

plugin.identity.keycloak:
  keycloak-issuer-url: https://<keycloak>/realms/<realm>
  keycloak-admin-url: https://<keycloak>/admin/realms/<realm>
  client-id: <client>
  client-secret: <secret>
  use-username-as-camunda-user-id: true
  administrator-group-name: cadenzaflow-admin
```

Do not configure `cadenzaflow.bpm.admin-user`. The identity provider is
read-only, so the engine cannot create that user. The administrators are the
members of the group named in `administrator-group-name`.

[`examples/boot4-app/`](examples/boot4-app/) is this setup as a complete
application, with an integration test that starts the engine against a real
Keycloak.

For single sign-on in the webapps of your application, add the platform's
`cadenzaflow-bpm-spring-boot-starter-security` (Spring Boot 4:
`cadenzaflow-bpm-spring-boot-starter-security-4`) and use the
`spring.security.oauth2.client` and `cadenzaflow.bpm.oauth2` settings from
[step 2 of the quickstart](#step-2--cadenzaflow-run). We validated single
sign-on on CadenzaFlow Run; an application with an embedded engine uses the
same starter and the same settings.

## Configuration reference

The properties are the same for every way of registering the plugin. Some
names contain the word "Camunda"; that is their name in the code.

| Property | Default | Meaning |
|---|---|---|
| `keycloakIssuerUrl` | | Issuer URL of the realm: `https://<keycloak>/realms/<realm>` |
| `keycloakAdminUrl` | | Admin REST URL of the realm: `https://<keycloak>/admin/realms/<realm>` |
| `clientId` | | The client the plugin signs in with. It needs a client secret, service accounts turned on, and the `realm-management` roles `query-groups`, `query-users` and `view-users`. |
| `clientSecret` | | Secret of that client |
| `useUsernameAsCamundaUserId` | `false` | Use the Keycloak username as the engine's user id |
| `useEmailAsCamundaUserId` | `false` | Use the Keycloak email as the engine's user id |
| `useGroupPathAsCamundaGroupId` | `false` | Use the group path as the engine's group id. Gives readable group ids in authorizations. |
| `administratorGroupName` | | With engine authorization on, the plugin grants this group all permissions on all resources |
| `administratorUserId` | | The same for a single user |
| `enforceSubgroupsInGroupQuery` | `false` | Set to `true` if you use subgroups with Keycloak 23 or newer: a plain group query does not return subgroups there |
| `authorizationCheckEnabled` | `true` | Check the engine's authorizations in user and group queries. `false` makes these queries faster in realms with very many users or groups. |
| `maxResultSize` | `250` | Most results the plugin reads from Keycloak for one query |
| `maxHttpConnections` | `50` | Size of the connection pool towards Keycloak |
| `disableSSLCertificateValidation` | `false` | For test systems only |
| `truststore`, `truststorePassword` | | A truststore file to use in place of the Java default |
| `proxyUri`, `proxyUser`, `proxyPassword` | | An HTTP proxy between the engine and Keycloak |

If neither `useUsernameAsCamundaUserId` nor `useEmailAsCamundaUserId` is set,
the user id is Keycloak's internal id.

With single sign-on, the login and the plugin must agree on the user id. Set
`user-name-attribute` of the Spring Security provider to match:

| Plugin setting | `user-name-attribute` |
|---|---|
| `useUsernameAsCamundaUserId: true` | `preferred_username` |
| `useEmailAsCamundaUserId: true` | `email` |
| neither | `sub` |

An email address is unique in a realm only if the realm is set up that way.

### Caching

Every identity query of the engine becomes a REST call to Keycloak. Users and
groups do not change every second, so the plugin can cache the answers.

| Property | Default | Meaning |
|---|---|---|
| `cacheEnabled` | `false` | Cache user and group queries |
| `maxCacheSize` | `500` | Entries in that cache; the least used entries go first |
| `cacheExpirationTimeoutMin` | `15` | Minutes until a cached entry is dropped |
| `loginCacheEnabled` | `false` | Cache successful password checks |
| `loginCacheSize` | `50` | Entries in the login cache |
| `loginCacheExpirationTimeoutMin` | `15` | Minutes until a cached login is dropped |

The login cache is for clients that call the REST API with HTTP Basic
authentication (`cadenzaflow.bpm.run.auth.enabled: true` on Run), for example
many external task workers. Without it, every request asks Keycloak for a
token. With it, a password that was correct stays accepted until the entry
expires, even if it was changed in Keycloak in the meantime. Single sign-on
does not use the login cache.

## Limitations

* A group is of type SYSTEM if it is the administrator group or has a Keycloak
  group attribute `type` with the value `SYSTEM`. All other groups are of type
  WORKFLOW.
* Keycloak's REST API cannot filter and sort by everything the engine asks
  for. The plugin applies some filters and all sorting itself, on the results
  it has read. `maxResultSize` limits those results.
* Tenants are not supported: tenant queries return nothing.
* Login without single sign-on works for users whose password Keycloak checks
  itself or through an LDAP or Kerberos federation. Users who sign in through
  another identity provider need single sign-on.

## Examples

* [`examples/run-sso-docker/`](examples/run-sso-docker/): CadenzaFlow Run,
  Keycloak with a demo realm and this plugin, started with one
  `docker compose up --build`. Its README explains the authorization model:
  the token authenticates, Keycloak groups authorize.
* [`examples/run-sso-rbac-docker/`](examples/run-sso-rbac-docker/): the same,
  plus an `engine-rest` whose endpoints are authorized by claims of the access
  token.
* [`examples/boot4-app/`](examples/boot4-app/): a Spring Boot 4 application
  with an embedded engine and `cadenzaflow-keycloak-4`.

How authentication, identity lookup and authorization work together is
described in
[doc/identity-and-authorization.md](doc/identity-and-authorization.md).

## Building and testing

```
mvn verify
```

The tests need a Keycloak. Without further settings they start Keycloak 26.5.6
as a container on port 8080, so Docker must be running. To use a Keycloak that
is already running, set:

| Environment variable | Meaning |
|---|---|
| `KEYCLOAK_URL` | URL of the Keycloak server. If it is set, no container is started. |
| `KEYCLOAK_USER` | A Keycloak admin user |
| `KEYCLOAK_PASSWORD` | Password of that user |
| `KEYCLOAK_ENFORCE_SUBGROUPS_IN_GROUP_QUERY` | `true` for Keycloak 23 or newer |

The tests run for `cadenzaflow-keycloak`; the other two artifacts share its
sources.

## Issues

Report problems at
[github.com/cadenzaflow/cadenzaflow-keycloak/issues](https://github.com/cadenzaflow/cadenzaflow-keycloak/issues).

## Origin and license

The plugin was written by Gunnar von der Beck (Accso - Accelerated Solutions
GmbH) as the community project
[camunda-platform-7-keycloak](https://github.com/camunda-community-hub/camunda-platform-7-keycloak).
This repository is a fork of its continuation,
[cibseven-keycloak](https://github.com/cibseven-community-hub/cibseven-keycloak).
The code was moved to the `org.cadenzaflow` packages and is built and tested
against the CadenzaFlow engine. Of the original modules it carries the
identity provider only.

Licensed under the [Apache License 2.0](LICENSE).
