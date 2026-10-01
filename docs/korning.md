# Köra exemplet

Kräver JDK 25 eller senare. JDK 26 används också i CI med Temurin.

## 1. Autentisera mot GitHub Packages

Följ [beroenden.md](beroenden.md): server-id `github` i `~/.m2/settings.xml`, token med `read:packages`.

## 2. Bygg och testa

```bash
./mvnw -B -U verify
```

`verify` kör enhetstester och `OrderPlatformIT`: bootstrap, `river deploy` av båda komponenterna, klientanrop, `river monitor`, `river undeploy`.

Om Maven inte kan hämta `se.gradinit.river:gradinit-river-bom:3.0.0-gradinit-SNAPSHOT` är publiceringen eller paketåtkomsten inte klar. Felmeddelandet i CI säger samma sak.

## 3. Manuellt flöde

```bash
./scripts/run-demo.sh
```

Skriptet gör samma steg och skriver bootstrap-loggen till `target/demo-logs`. Motsvarande kommandon för hand, efter `./mvnw -B -U -DskipITs package`:

```bash
mapfile -t FLAGS < <(./scripts/river-jvm-flags.sh)
JAVA="${JAVA_HOME:-}/bin/java"
# SNAPSHOT-filnamn är tidsstämplade. river-jvm-flags.sh och run-demo.sh väljer senaste jar.
"$JAVA" "${FLAGS[@]}" -jar <platform-bootstrap.jar>
"$JAVA" "${FLAGS[@]}" -jar <platform-cli.jar> deploy customer-component/target/customer-component-1.0.0.jar
"$JAVA" "${FLAGS[@]}" -jar <platform-cli.jar> deploy order-component/target/order-component-1.0.0.jar
"$JAVA" "${FLAGS[@]}" -jar <platform-cli.jar> monitor
"$JAVA" "${FLAGS[@]}" -cp "client/target/client-1.0.0.jar:$(cat client/target/classpath.txt)" \
  se.gradinit.riverexample.client.OrderClient alice SKU-100 1
"$JAVA" "${FLAGS[@]}" -jar <platform-cli.jar> undeploy order
"$JAVA" "${FLAGS[@]}" -jar <platform-cli.jar> undeploy customer
```

Starta bootstrap i en egen terminal. Klienten skriver två rader `ORDER_OK` med samma `backendId`.

Varje process som exporterar en tjänst får ett eget `instanceId` via `-Driver.instance=...`. Utan den blir id:t `0`, och `ServiceIdFile.defaultPath` pekar då på samma fil för alla processer med det id:t.

## JVM-flaggor

På JDK 17 och senare finns inte `java.rmi.activation` i plattformsmodulen. GradinITRiver kräver:

```text
--patch-module java.rmi=<compat-rmi-activation.jar>
--add-exports java.rmi/java.rmi.activation=ALL-UNNAMED
```

`scripts/river-jvm-flags.sh` hittar SNAPSHOT-jaren (inte `*-sources.jar` eller `*-javadoc.jar`) och skriver flaggorna. Surefire och failsafe pekar på en kopia i `target/river-jvm/compat-rmi-activation.jar`.

Lookup-URL är `jini://127.0.0.1:4160` om inte `-Dse.gradinit.river.lookup=...` sätts. Multicast-discovery är också på.
