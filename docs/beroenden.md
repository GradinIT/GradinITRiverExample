# Beroenden mot GradinITRiver

Exemplet kompilerar mot GradinITRivers JAR-filer från GitHub Packages. Källkod kopieras inte hit, och repot checkas inte ut i CI.

## Pin

Versionen ligger i rotens `pom.xml`:

```xml
<gradinit.river.version>3.0.0-gradinit-SNAPSHOT</gradinit.river.version>
```

Push till `develop` i GradinITRiver publicerar `3.0.0-gradinit-SNAPSHOT`. Taggen `v3.0.0-gradinit` publicerar releasen `3.0.0-gradinit`. Byt propertyn när du vill bygga mot releasen.

Beroendena importeras från `se.gradinit.river:gradinit-river-bom`. Repot i `pom.xml` har id `github` och URL `https://maven.pkg.github.com/GradinIT/GradinITRiver`.

Publicerade artefakter, med sources- och javadoc-JAR:

| artifactId | Roll här |
| --- | --- |
| `gradinit-river` | föräldra-POM |
| `gradinit-river-bom` | versioner |
| `platform-api` | `@ExportedService`, `@Routing`, `ServiceExporter`, `ServiceIdFile`, `HrwSelector`, `RoutingKeys` |
| `platform-bootstrap` | startar plattformen, bland annat Reggie |
| `platform-cli` | `river deploy`, `undeploy`, `list`, `status`, `monitor` |
| `platform-deployer` | läser SLA och konfiguration |
| `platform-supervisor` | startar om instanser med samma ServiceID |
| `compat-rmi-activation` | patch för `java.rmi` på JDK 17+ |
| `jsk-platform`, `jsk-resources`, `jsk-lib`, `jsk-dl` | Jini/JERI |
| `start`, `reggie`, `mahalo`, `outrigger`, `norm`, `mercury`, `fiddler`, `phoenix` | River-tjänster |
| `river-extra` | extra River-stöd |

## Lokal `settings.xml`

GitHub Packages kräver autentisering även för läsning. Lägg detta i `~/.m2/settings.xml`. `<id>` ska vara `github`, samma id som `<repository>` i `pom.xml`.

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>DITT_GITHUB_ANVÄNDARNAMN</username>
      <password>TOKEN_MED_read:packages</password>
    </server>
  </servers>
</settings>
```

Token är en classic personal access token med scope `read:packages` (samma sorts token som hemligheten `GRADINIT_PACKAGES_TOKEN`). Användarnamnet är GitHub-kontot som äger token. För de här paketen är det `GradinIT`.

Kontrollera upplösningen:

```bash
./mvnw -B -U dependency:resolve
```

## Actions i det här repot

GradinIT är ett personligt konto. Då finns inte **Manage Actions access** per paket, och `GITHUB_TOKEN` från det här repot kan inte läsa `https://maven.pkg.github.com/GradinIT/GradinITRiver`.

CI skriver därför en `settings.xml` med server-id `github`, användarnamn `GradinIT` (kontot som äger token; `${{ github.actor }}` fungerar när den som startar jobbet är samma konto) och lösenord `${{ secrets.GRADINIT_PACKAGES_TOKEN }}`. Hemligheten ska vara en classic PAT med `read:packages`. Workflowen finns i [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

## JitPack

JitPack används inte. Förälderns koordinater är `se.gradinit.river:gradinit-river`, bygget är multi-modul, och versionen ska vara den som GitHub Packages publicerar.
