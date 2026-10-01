# Sådant som försvårar extern användning av GradinITRiver

Det här är iakttagelser från att bygga ett fristående repo mot plattformen. Inget av det är ändrat i GradinITRiver.

## Artefakterna publiceras inte

`se.gradinit.river` finns inte på Maven Central. Ett konsumentrepo kan inte skriva ett vanligt beroende och låta Maven lösa det, förrän någon har kört `install` eller `deploy`. Rekommendationen är GitHub Packages, beskriven i [beroenden.md](beroenden.md).

## Repot är inte läsbart med vanlig checkout

`https://github.com/GradinIT/GradinITRiver` svarar 404 för både anonym läsning och den token som kan läsa `GradinITRiverExample` (contents-API, git clone och råfiler). Kodindexet har ändå träffar på `develop`, med version `3.0.0-gradinit` och förälder `se.gradinit.river:gradinit-river`. Antingen är repot privat, eller så är det inte delat med den här installationen.

Följden är att CI i det här repot bara blir grön om `develop` är publik eller om `GRADINIT_RIVER_TOKEN` har läsrättighet. Utan det faller checkout-steget.

## JVM-flaggor krävs

`--patch-module java.rmi=...` och `--add-exports java.rmi/java.rmi.activation=ALL-UNNAMED` behövs på JDK 25 och 26. De kommer från `compat-rmi-activation` och är lätta att missa om man bara lägger plattformen på klassökvägen.

## Konfigurationsnamnrymden är inte med i de fragment som gick att läsa

SLA-formatet är bekräftat från plattformens tester: rot `component` i namnrymden `urn:se:gradinit:river:sla`, med attributen `name` och `version`, och `service` med attributen `interface`, `name`, `instances` och `mainClass`. Hello-IT:t använder samma form för tre backendar och en router.

`META-INF/{namn}_conf.xml` ska namnge samma komponent och lista beroenden på gränssnitt plus `Name`. Exemplet använder namnrymden `urn:se:gradinit:river:conf` och ett `dependency`-element med attributen `interface` och `name`. Om schemats namnrymd eller elementnamn skiljer sig avvisar `river deploy` JAR-filen, och XML:en ska rättas mot plattformens `examples/` och `ComponentDescriptors`.

## Hjälpklasser i platform-api

`ServiceExporter`, `ServiceIdFile`, `RoutingKeys` och HRW ligger i `platform-api`. Hello-exemplet exporterar enligt dokumentationen med `BasicJeriExporter` och `JoinManager`, och det gör det här exemplet också. HRW-valet görs i `example-support` på fältet som är märkt `@Routing`, eftersom den exakta hjälpmetoden inte finns i en publicerad javadoc. När signaturen är känd bör routern anropa plattformens HRW i stället för den lokala CRC32-varianten.

## Läget integrity

Standardläget `integrity` kräver att API-JAR, `jsk-platform` och `reggie` finns på klientens klassökväg, eller att kodbasen är `file:` eller `httpmd:`. Klientmodulen beror därför på `jsk-platform` och `reggie`.
