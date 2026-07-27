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
mvn verify -Dversion.cadenzaflow=1.2.0      # e.g. to reproduce the old failure
```

## Status: green against published artifacts (platform ≥ 1.2.1)

Verified 2026-07-27 in a **clean local repository**, resolving only from the
CadenzaFlow Nexus and Maven Central: platform **1.2.1** + plugin **1.1.1** →
2/2 tests, engine boots on Spring Boot 4 / Spring Framework 7, users and
groups federate from a real Keycloak, admin authorizations seeded. This runs
in CI on every push.

It earned its keep on day one: against platform **1.2.0** the same example
could not even resolve its dependencies, which is how the defect below was
found and fixed.

## The bug this example caught (fixed in platform 1.2.1)

Against platform 1.2.0 the build failed with:

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

Fixed by the full-reactor 1.2.1 release: cadenzaflow-parent:1.2.1 now carries
the property, and this example verifies it on every run. To check another
version: mvn verify -Dversion.cadenzaflow=<version>
