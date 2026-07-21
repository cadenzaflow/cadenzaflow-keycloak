# CadenzaFlow Run + Keycloak SSO — buildable Docker example

A complete, self-contained single-sign-on setup you can build and run in one
command: CadenzaFlow Run 1.2.0 + the `cadenzaflow-keycloak` identity plugin
1.0.0 + Keycloak 26 with a demo realm. The configuration is the one validated
end-to-end against a live Keycloak (login redirect, real-browser SSO
round-trip, identity federation, admin authorization seeding).

## Run it

```bash
docker compose up --build
```

Then open **http://localhost:8096/cadenzaflow/app/cockpit/default/** — you are
redirected to the Keycloak login. Demo users (from `realm/`):

| User | Password | Keycloak group | Result |
|---|---|---|---|
| `kermit` | `kermit` | `cadenzaflow-admin` | Full admin (Cockpit/Admin/Tasklist) |
| `gonzo` | `gonzo` | `accounting` | Regular user, no admin rights |

Keycloak admin console: http://localhost:8095 (`admin` / `admin`).

Quick API check (identity federation — none of this lives in the engine DB):

```bash
curl http://localhost:8096/engine-rest/group?member=kermit
# -> [{"id":"...","name":"cadenzaflow-admin","type":"SYSTEM"}]
```

## The authorization model — read this before mapping your roles

This setup makes a deliberate architectural split:

* **The access token authenticates.** It proves *who* is calling
  (`preferred_username`). **Roles inside the token
  (`realm_access.roles`, `resource_access.*.roles`) are NOT consulted for
  authorization** — neither by the webapps nor by `engine-rest`.
* **Keycloak groups authorize.** The engine reads the user's *groups* live
  from Keycloak (read-only federation via this plugin) and applies its own
  authorization tables (`ACT_RU_AUTHORIZATION`) to those groups. The
  `administratorGroupName` config seeds full ADMIN grants for one group
  automatically at first boot; every other permission is granted per group in
  the Admin webapp (or via the REST/Java API) once.

So to drive permissions from Keycloak: **model them as Keycloak groups, not
roles**. Put users into groups; grant engine permissions to those groups once.
Membership changes in Keycloak take effect in CadenzaFlow immediately — that
is the same operational outcome as token-role mapping, with the engine's full
resource/permission granularity (per process definition, per task, etc.)
instead of coarse endpoint-level roles.

If your integration strictly requires "whatever the token says is the
permission" (stateless, no engine authorization tables), that is currently
**not implemented** — talk to us with the concrete claim layout before
building around that assumption.

The full mechanics — request flows for browser and REST, how to grant
permissions, machine clients via service accounts, and the roles-vs-groups
rationale — are in
[doc/identity-and-authorization.md](../../doc/identity-and-authorization.md).

`engine-rest` in this example is open (no auth filter) to keep the demo
simple. To protect it with the same Keycloak (Bearer tokens,
`client_credentials`/`password` grants), enable the OIDC Bearer authentication
provider that ships with the platform — see the *engine-rest* README (section
"OIDC Bearer authentication") in `cadenzaflow-bpm-platform`. The same
group-based authorization then applies to REST calls.

## What the pieces do

| File | Purpose |
|---|---|
| `Dockerfile` | Layers on the official [`cadenzaflow/cadenzaflow-bpm-platform:run-1.2.0`](https://hub.docker.com/r/cadenzaflow/cadenzaflow-bpm-platform) image: plugin jar (CadenzaFlow Nexus) + its deps (Maven Central) + `config/default.yml` + `--oauth2` |
| `docker-compose.yml` | Keycloak 26 (demo realm auto-import, `KC_HOSTNAME` pinned) + the built image |
| `config/default.yml` | The validated SSO recipe: oauth2 client (split-URL variant), plugin registration, `authorization.enabled` |
| `realm/cadenzaflow-realm.json` | Demo realm: confidential client + service-account roles, `cadenzaflow-admin` group, demo users |

## Adapting it for real use

* Change the client secret (`realm/` **and** `config/default.yml`) and the
  demo passwords; put secrets in environment variables, not in files.
* Replace the in-container H2 with a real database (`spring.datasource`).
* Terminate TLS in front of both services; then `KC_HOSTNAME` becomes your
  public Keycloak URL and the two-URL split may collapse into one
  (`issuer-uri` alone is enough when browser and Run use the same URL — see
  the Quickstart in the repository README).
* Keep the Keycloak group for admins named **`cadenzaflow-admin`** if you
  can — that exact name is the engine's built-in admin constant and is
  auto-typed SYSTEM everywhere.

## Pitfalls this example already handles

| Pitfall | Where it is handled |
|---|---|
| Login bounces to `/login?error` (userinfo 401) with split URLs | `KC_HOSTNAME` + `KC_HOSTNAME_BACKCHANNEL_DYNAMIC` in `docker-compose.yml` |
| Authorization flag silently ignored | first-class `cadenzaflow.bpm.authorization.enabled` in `config/default.yml` (never generic-properties) |
| No admin after first login | `administratorGroupName: cadenzaflow-admin` + seeded ADMIN grants (watch for `KEYCLOAK-01002 GRANT` boot log lines) |
| Redirect URI mismatch | explicit `redirect-uri` in the oauth2 registration (no `issuer-uri` discovery in split-URL mode) |
