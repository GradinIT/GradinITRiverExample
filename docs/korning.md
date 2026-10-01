# Köra exemplet

Kräver JDK 25 eller senare och git. JDK 26 fungerar i CI med Temurin.

## 1. Installera plattformen

```bash
./scripts/install-gradinit-river.sh
```

Det checkar ut `GradinITRiver` på `develop` och kör `./mvnw -B install -DskipTests`.

## 2. Bygg och testa

```bash
./mvnw -B verify
```

`verify` kör enhetstester och `OrderPlatformIT`: bootstrap, `river deploy` av båda komponenterna, klientanrop, `river monitor`, `river undeploy`.

## 3. Manuellt flöde

```bash
./scripts/run-demo.sh
```

Skriptet gör samma steg och skriver bootstrap-loggen till `target/demo-logs`. Motsvarande kommandon för hand, efter `./mvnw -B -DskipITs package`:

```bash
mapfile -t FLAGS < <(./scripts/river-jvm-flags.sh)
JAVA="${JAVA_HOME:-}/bin/java"
VER=3.0.0-gradinit
M2="$HOME/.m2/repository/se/gradinit/river"

"$JAVA" "${FLAGS[@]}" -jar "$M2/platform-bootstrap/$VER/platform-bootstrap-$VER.jar"
"$JAVA" "${FLAGS[@]}" -jar "$M2/platform-cli/$VER/platform-cli-$VER.jar" deploy customer-component/target/customer-component-1.0.0.jar
"$JAVA" "${FLAGS[@]}" -jar "$M2/platform-cli/$VER/platform-cli-$VER.jar" deploy order-component/target/order-component-1.0.0.jar
"$JAVA" "${FLAGS[@]}" -jar "$M2/platform-cli/$VER/platform-cli-$VER.jar" monitor
# klassökväg: client/target/client-1.0.0.jar plus client/target/classpath.txt
"$JAVA" "${FLAGS[@]}" -cp "client/target/client-1.0.0.jar:$(cat client/target/classpath.txt)" \
  se.gradinit.riverexample.client.OrderClient alice SKU-100 1
"$JAVA" "${FLAGS[@]}" -jar "$M2/platform-cli/$VER/platform-cli-$VER.jar" undeploy order
"$JAVA" "${FLAGS[@]}" -jar "$M2/platform-cli/$VER/platform-cli-$VER.jar" undeploy customer
```

Starta bootstrap i en egen terminal. Klienten skriver två rader `ORDER_OK` med samma `backendId`.

## JVM-flaggor

På JDK 17 och senare finns inte `java.rmi.activation` i plattformsmodulen. GradinITRiver kräver:

```text
--patch-module java.rmi=<lokal-repo>/se/gradinit/river/compat-rmi-activation/3.0.0-gradinit/compat-rmi-activation-3.0.0-gradinit.jar
--add-exports java.rmi/java.rmi.activation=ALL-UNNAMED
```

`scripts/river-jvm-flags.sh` skriver flaggorna. Samma flaggor sätts på integrationstestets JVM.

Lookup-URL är `jini://127.0.0.1:4160` om inte `-Dse.gradinit.river.lookup=...` sätts. Multicast-discovery är också på.
