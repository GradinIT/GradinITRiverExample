# Beroenden mot GradinITRiver

Exemplet kompilerar mot GradinITRivers JAR-filer. Källkod kopieras inte hit.

## Pin

Versionen ligger i rotens `pom.xml`:

```xml
<gradinit.river.version>3.0.0-gradinit</gradinit.river.version>
```

Den ska matcha `se.gradinit.river:gradinit-river` på grenen `develop`. Moduler som exemplet använder:

| artifactId | Roll |
| --- | --- |
| `platform-api` | `@ExportedService`, `@Routing`, descriptor-typer |
| `platform-bootstrap` | startar plattformen, bland annat Reggie |
| `platform-cli` | `river deploy`, `undeploy`, `list`, `status`, `monitor` |
| `platform-deployer` | läser SLA och konfiguration (transitivt via bootstrap) |
| `platform-supervisor` | startar om instanser med samma ServiceID |
| `jsk-platform` | Jini/JERI, krävs på klientens klassökväg i läget `integrity` |
| `reggie` | lookup-tjänsten, samma krav i läget `integrity` |
| `compat-rmi-activation` | patch för `java.rmi` på JDK 17+ |

## a) Lokal installation från `develop` (det här repot)

Artefakterna finns inte på Maven Central. CI och lokal utveckling bygger därför GradinITRiver och installerar JAR-filerna i det lokala Maven-repot innan exemplet byggs.

Lokalt:

```bash
./scripts/install-gradinit-river.sh
./mvnw -B verify
```

Skriptet klonar `https://github.com/GradinIT/GradinITRiver.git` på `develop` till `.upstream/GradinITRiver` och kör `./mvnw -B install -DskipTests`. Miljövariablerna `GRADINIT_RIVER_URL`, `GRADINIT_RIVER_REF` och `GRADINIT_RIVER_SRC` kan peka om källan.

CI gör samma sak: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) checkar ut `GradinIT/GradinITRiver@develop` och installerar innan `./mvnw -B verify`. Bygget körs på JDK 25 och JDK 26.

Det här är den robusta vägen så länge plattformen inte publicerar versionerade artefakter. Nackdelen är att exemplet bara byggs om `develop` går att läsa. Ett privat repo kräver hemligheten `GRADINIT_RIVER_TOKEN` (en PAT med `contents: read`). Utan den använder workflowen `github.token`, som bara läser publika repon.

## b) JitPack

JitPack kan bygga ett GitHub-repo utan att artefakterna publiceras i förväg. Det passar dåligt här:

- förälderns koordinater är `se.gradinit.river:gradinit-river:3.0.0-gradinit`, inte JitPacks `com.github.GradinIT:GradinITRiver`
- bygget är multi-modul och behöver egna JVM-flaggor för tester
- ett privat repo kräver en JitPack-token, och `develop` rör sig utan en oföränderlig version

Därför används inte JitPack.

## c) Rekommendation: GitHub Packages

Det här repot ändrar inte GradinITRiver. Rekommendationen till plattformsrepot är att publicera modulerna till GitHub Packages när `develop` byggs grönt:

- `distributionManagement` mot `https://maven.pkg.github.com/GradinIT/GradinITRiver`
- ett workflow som kör `./mvnw -B deploy -DskipTests` med `GITHUB_TOKEN` och scope `packages: write`
- behåll `groupId` `se.gradinit.river` och versionen `3.0.0-gradinit` tills en release-version klipps
- publicera åtminstone `platform-api`, `platform-bootstrap`, `platform-cli`, `platform-deployer`, `platform-supervisor`, `jsk-platform`, `reggie` och `compat-rmi-activation`

Då kan exemplet byta ut checkout-steget mot en `repository` i `pom.xml` och fortsätta peka på `gradinit.river.version`. Konsumenter behöver fortfarande läsrättighet till paketen om de är privata.
