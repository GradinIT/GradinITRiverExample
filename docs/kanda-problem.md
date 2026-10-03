# Sådant som försvårar extern användning av GradinITRiver

## Paketen kräver en PAT

`3.0.0-gradinit-SNAPSHOT` publiceras till `https://maven.pkg.github.com/GradinIT/GradinITRiver` (server-id `github`). Taggen `v3.0.0-gradinit` publicerar `3.0.0-gradinit`.

GradinIT är ett personligt konto, så paket kan inte delas med det här repots `GITHUB_TOKEN` via Manage Actions access. CI och lokal utveckling använder en classic PAT med `read:packages`: hemligheten `GRADINIT_PACKAGES_TOKEN` i Actions, och samma token i `~/.m2/settings.xml` lokalt. Se [beroenden.md](beroenden.md).

## JVM-flaggor krävs

`--patch-module java.rmi=...` och `--add-exports java.rmi/java.rmi.activation=ALL-UNNAMED` behövs på JDK 25 och 26. De kommer från `compat-rmi-activation`. SNAPSHOT-jaren i det lokala repot är tidsstämplade, så sökvägen kan inte hårdkodas mot `artifact-3.0.0-gradinit-SNAPSHOT.jar`.

`bin/river-platform` sätter `--patch-module` och `--add-exports` på sin egen JVM, och supervisorn skickar samma flaggor till komponenternas barn-JVM. Lägg dem inte också i `JDK_JAVA_OPTIONS`. Då patchas `java.rmi` två gånger och barn-JVM:en startar inte.

## SLA och konfiguration

`META-INF/SLA.xml` har namnrymden `urn:se:gradinit:river:sla` och rotelementet `component`. `META-INF/{namn}_conf.xml` har namnrymden `urn:se:gradinit:river:conf` och rotelementet `configuration`. Ett beroende skrivs som `depends` med attributen `interface` och `name`.

`ComponentDescriptors` i plattformen sätter `SLA_NAMESPACE` till `urn:se:gradinit:river:sla` och `CONF_NAMESPACE` till `urn:se:gradinit:river:conf`, och läser rotelementen `component` respektive `configuration`.

## Plattformen startas från distributionen

`PlatformRuntime.activationClasses()` letar efter `compat-rmi-activation/target/classes` i GradinITRivers källträd och fungerar inte från ett konsumentrepo. Den publicerade SNAPSHOT löser det med `PlatformMain`: `gradinit-river-dist` innehåller `bin/river-platform`, `bin/river` och `bin/river-web-console`. `bin/river-platform --clean` startar en tom plattform i en egen JVM och skriver `RIVER_PLATFORM_READY jini://host:port`. Skripten slår upp activation-jaren på klassökvägen. Exemplet startar inte `PlatformRuntime` själv.

`bin/river-platform` och `bin/river` är de startpunkter som ska användas. `java -jar` på `platform-cli` eller `platform-bootstrap` ser inte `jsk-platform`, och `RiverCommand` behöver `net.jini.discovery.DiscoveryManagement`. Exempelklienten startas fortfarande med egen klassökväg och JVM-flaggorna från `scripts/river-jvm-flags.sh`.

## Läget integrity

Standardläget `integrity` kräver att API-JAR, `jsk-platform` och `reggie` finns på klientens klassökväg, eller att kodbasen är `file:` eller `httpmd:`. Klientmodulen beror därför på `jsk-platform` och `reggie`.
