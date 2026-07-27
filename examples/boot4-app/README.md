# Example: Spring Boot 4 application with CadenzaFlow + Keycloak

A realistic embedded application — the shape customers actually build:

* **Spring Boot 4** (`spring-boot-starter-parent` 4.0.7, Spring Framework 7)
* **`cadenzaflow-bpm-spring-boot-starter-4`** — the engine, auto-configured
* **`cadenzaflow-keycloak-4`** — users and groups read live from Keycloak
* everything resolved from **published artifacts** (CadenzaFlow Nexus +
  Maven Central), not from a local build

The application code is deliberately tiny: one `@SpringBootApplication` class
that publishes the identity plugin as a bean, bound from
`plugin.identity.keycloak.*` in `application.yaml` — the same configuration
surface the Run distribution uses.

## Why this example is also a test

`KeycloakFederationIT` starts a real Keycloak (Testcontainers) with the demo
realm, boots the engine against it and asserts that

* `kermit` and `gonzo` resolve as users **from Keycloak** (the engine database
  has no users), each in their own group, and
* the `cadenzaflow-admin` group got its ADMIN authorizations seeded at engine
  build.

So it fails if any of these regress: the plugin on Spring Framework 7, the
admin bootstrap, **or the published POM chains of the artifacts it consumes**.
That last one is not theoretical — see below.

```bash
mvn verify                                  # needs Docker for Testcontainers
mvn verify -Dversion.cadenzaflow=1.2.1      # try another platform version
```

## Status: the example is proven; the published starters are not

Two runs, two different answers — which is exactly what makes this useful:

| Resolved from | Result |
|---|---|
| a locally built platform (consistent POM chain) | ✅ **green** — 2/2 tests, engine boots on Spring Boot 4, federation and admin seeding verified against a real Keycloak |
| **published** artifacts only (clean local repository) | ❌ **fails before compiling** — see below |

So the application code, the plugin's `-4` line and the test are correct; what
is broken is the *publication* of the platform starters.

## Known failure today: the published `-4` starters cannot be consumed

As of 2026-07-27 this example **does not build against platform 1.2.0**:

```
Failed to read artifact descriptor for
  org.cadenzaflow.bpm.springboot:cadenzaflow-bpm-spring-boot-starter-4:jar:1.2.0
Downloading ... /spring-framework-bom/${version.spring.framework7}/...
```

`cadenzaflow-bpm-spring-boot-4-starter-root:1.2.0` imports
`spring-framework-bom` at `${version.spring.framework7}`, but that property is
defined in **no published parent POM** (it was added to the platform's
`parent/pom.xml` after those parents were published, and the `-4` modules were
deployed on their own). Walking the published chain:

```
starter-4:1.2.0 -> 4-starter-root:1.2.0        (USES the property)
  -> database-settings:1.2.0 -> parent:1.2.0 -> root:1.2.0 -> release-parent:1.0.0
                                                (defined in NONE of them)
```

Worth knowing why this went unnoticed: **`mvn dependency:get` on the same
artifacts succeeds** — that path tolerates an unresolvable import. Only a real
project collecting its dependency tree fails. A published artifact can look
fine to a spot check and still be unusable.

The fix is a full-reactor platform release (master already defines the
property). Once that is out, run this example with the new version; when it
goes green, consumption is proven for real.
