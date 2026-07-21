# CadenzaFlow: SSO webapps + token-claim RBAC on engine-rest

The setup [opentmf/opentmf-camunda7](https://github.com/opentmf/opentmf-camunda7)
users asked for, on CadenzaFlow: **Keycloak SSO for the webapps** and an
**engine-rest gated by the claims of the access token**, using OpenTMF's own
[openid-rbac-security](https://github.com/opentmf/openid-rbac-security)
(Maven Central) so the semantics match that stack exactly.

```
Browser ──authorization_code──► webapp node :8096 ─┐
                                                   ├─ shared PostgreSQL (one engine cluster)
Service ──Bearer (groups claim)─► rest node :8097 ─┘
                 ▲
                 └── method+path rules: GET needs accounting|cadenzaflow-admin,
                     POST/PUT/DELETE need cadenzaflow-admin
```

## Run it

```bash
docker compose up --build
```

* **Webapps (SSO):** http://localhost:8096/cadenzaflow/app/cockpit/default/ —
  redirects to Keycloak; `kermit`/`kermit` is a full admin, `gonzo`/`gonzo` a
  regular user.
* **REST (token-gated):**

  ```bash
  TOKEN=$(curl -s -X POST http://localhost:8095/realms/cadenzaflow/protocol/openid-connect/token \
    -d 'grant_type=password&client_id=cadenzaflow-webapps&client_secret=change-me-demo-secret&username=gonzo&password=gonzo' \
    | jq -r .access_token)

  curl -i        http://localhost:8097/engine-rest/task                    # 401 without a token
  curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8097/engine-rest/task        # 200 (GET allowed for accounting)
  curl -i -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
       -d '{}' http://localhost:8097/engine-rest/task                      # 403 (POST needs cadenzaflow-admin)
  ```

  Verified matrix: no token → 401; `gonzo` (accounting) GET → 200, POST →
  403; `kermit` (cadenzaflow-admin) GET/POST → 200.

The "roles" in the rules are the values of the token's **`groups` claim** — a
Keycloak group-membership mapper on the client puts them there
(`realm/cadenzaflow-realm.json`). This is exactly how opentmf-camunda7 uses
the library (`authorities-claim: groups`). Machine clients get access by
adding their **service account** user to a Keycloak group.

## Why two nodes?

Both the SSO login chain (CadenzaFlow `starter-security`) and the RBAC chain
(openid-rbac-security) currently register a Spring Security filter chain that
matches **any request** — two such chains cannot coexist in one JVM
(`UnreachableFilterChainException` at boot). Until the platform scopes its
login chain to the webapp paths (planned), the clean composition is one node
per concern, sharing the engine database:

| Node | Modules | Security | How |
|---|---|---|---|
| `webapp` :8096 | `--webapps --rest --oauth2` | Keycloak SSO (authorization_code) | oauth2 client registration configured; the four `org.opentmf.security.config.*` auto-configurations excluded |
| `rest` :8097 | `--rest --oauth2`¹ | token-claim RBAC | `opentmf.security.*` configured; no client registration (SSO chain never activates) + the starter's permit-all fallback excluded |

¹ `--oauth2` on the rest node only supplies the spring-security jars.

Both nodes run the `cadenzaflow-keycloak` identity plugin against the same
Keycloak, and engine authorization is ON — so on top of the coarse token gate,
the engine still enforces its own fine-grained group permissions (see
[doc/identity-and-authorization.md](../../doc/identity-and-authorization.md)
for how the two layers compose). One image serves both nodes; the mounted
config decides the role.

## Files

| File | Purpose |
|---|---|
| `Dockerfile` | official Run image + identity plugin + openid-rbac-security (+ resource-server jar) + PostgreSQL driver |
| `docker-compose.yml` | Keycloak (demo realm, `KC_HOSTNAME` pinned) + PostgreSQL + the two nodes |
| `config/webapp.yml` | validated SSO recipe (see `../run-sso-docker`), RBAC auto-configs excluded |
| `config/rest.yml` | `opentmf.security` rules + permit-all fallback excluded |
| `realm/cadenzaflow-realm.json` | demo realm incl. the `groups` token mapper |

## Adapting for real use

Everything from `../run-sso-docker` (change secrets, TLS, real DB creds)
applies. Additionally:

* Edit the `secure-endpoints` rules in `config/rest.yml` to your group names
  and path policy; the whitelist is intentionally minimal (`/error`) — add
  `/engine-rest/external-task/**` only if your workers must run tokenless
  (opentmf-camunda7 does this; consider giving workers a client of their own
  instead).
* First boot may restart the nodes once or twice while Keycloak imports the
  realm and the schema is created — that is the `restart: on-failure` policy
  doing its job.
