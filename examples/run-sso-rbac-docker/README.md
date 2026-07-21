# CadenzaFlow: one image - SSO webapps + token-claim RBAC on engine-rest

A **single CadenzaFlow docker image** that does both things
[opentmf/opentmf-camunda7](https://github.com/opentmf/opentmf-camunda7) users
asked for: **Keycloak single-sign-on for the webapps** and an **engine-rest
whose endpoints are authorized by the claims of the access token**, using
OpenTMF's own
[openid-rbac-security](https://github.com/opentmf/openid-rbac-security)
(Maven Central) so the semantics match that stack exactly.

```
                       ┌──────────── one CadenzaFlow instance :8096 ────────────┐
Browser ── authorization_code ──► /cadenzaflow/**  (SSO login chain)            │
Service ── Bearer (groups claim) ─► /engine-rest/** (token-claim RBAC chain)    │
                       └────────────────────────────────────────────────────────┘
```

## Run it

```bash
docker compose up --build
```

* **Webapps (SSO):** http://localhost:8096/cadenzaflow/app/cockpit/default/ —
  redirects to Keycloak; `kermit`/`kermit` is a full admin, `gonzo`/`gonzo` a
  regular user.
* **REST (token-gated), same instance:**

  ```bash
  TOKEN=$(curl -s -X POST http://localhost:8095/realms/cadenzaflow/protocol/openid-connect/token \
    -d 'grant_type=password&client_id=cadenzaflow-webapps&client_secret=change-me-demo-secret&username=gonzo&password=gonzo' \
    | jq -r .access_token)

  curl -i        http://localhost:8096/engine-rest/task                                   # 401 without a token
  curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8096/engine-rest/task        # 200 (GET allowed for accounting)
  curl -i -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
       -d '{}' http://localhost:8096/engine-rest/task                                     # 403 (POST needs cadenzaflow-admin)
  ```

Verified matrix (all on the one instance): no token → 401; `gonzo`
(accounting) GET → 200, POST → 403; `kermit` (cadenzaflow-admin) GET/POST →
200; plus the full real-browser SSO round-trip with admin access.

The "roles" in the rules are the values of the token's **`groups` claim** — a
Keycloak group-membership mapper on the client puts them there
(`realm/cadenzaflow-realm.json`), exactly how opentmf-camunda7 configures the
library (`authorities-claim: groups`). Machine clients get access by adding
their **service account** user to a Keycloak group. Prefer a different claim?
Change `opentmf.security.authorities-claim` in `config/default.yml`.

## How the two chains coexist (the sso-chain module)

The platform's oauth2 login chain currently matches *any request* and cannot
share a JVM with a second any-request chain. This example therefore excludes
that auto-configuration and ships a tiny glue module (`sso-chain/`, built in
the Dockerfile's first stage): the same login chain **scoped to the webapp
surface** via `securityMatcher` (`/cadenzaflow/**`, `/oauth2/**`, `/login/**`,
`/logout`), ordered before the RBAC chain, which then owns everything else —
notably `/engine-rest/**`. Two implementation notes captured for the planned
platform fix:

* Use `PathPatternRequestMatcher`, **not** `securityMatcher(String...)` — the
  string variant builds `MvcRequestMatcher`s, which never match the webapp's
  `/api` (a non-MVC JAX-RS servlet), producing 401s on Cockpit's own API.
* The RBAC library registers four auto-configurations
  (`org.opentmf.security.config.*`); an app that wants only one of the two
  chains excludes the other side's auto-configurations
  (see `config/default.yml`).

Engine authorization is ON as well: behind the coarse token gate, the engine
still enforces its fine-grained group permissions (see
[doc/identity-and-authorization.md](../../doc/identity-and-authorization.md)).

## Files

| File | Purpose |
|---|---|
| `Dockerfile` | stage 1: builds `sso-chain`; stage 2: official Run image + identity plugin + openid-rbac-security (+ resource-server jar) + glue jar + config |
| `sso-chain/` | the scoped SSO login chain (single class + auto-configuration registration) |
| `config/default.yml` | SSO client + identity plugin + `opentmf.security` rules + the auto-configuration exclude |
| `docker-compose.yml` | Keycloak (demo realm, `KC_HOSTNAME` pinned) + the instance |
| `realm/cadenzaflow-realm.json` | demo realm incl. the `groups` token mapper |

## Adapting for real use

Everything from `../run-sso-docker` applies (change secrets, TLS, real
database instead of the in-container H2). Edit the `secure-endpoints` rules in
`config/default.yml` to your group names and path policy; the whitelist is
intentionally minimal (`/`, `/error`) — add `/engine-rest/external-task/**`
only if your workers must run tokenless (consider a client of their own
instead). First boot may restart once while Keycloak imports the realm
(`restart: on-failure`).
