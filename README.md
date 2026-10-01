# GradinITRiverExample

Ett fristående exempel som använder [GradinITRiver](https://github.com/GradinIT/GradinITRiver) (`se.gradinit.river`, version `3.0.0-gradinit-SNAPSHOT`) som vanliga Maven-beroenden från GitHub Packages. Ingen plattformskod är kopierad hit.

`customer` är en komponent med en instans. `order` är en komponent med tre backendar och en router, och beror på kunden via `META-INF/order_conf.xml` (gränssnitt + Jini-`Name`). Klienten anropar routern. Routern väljer backend med plattformens HRW (`HrwSelector` och `RoutingKeys`) på `customerId`, märkt `@Routing`.

## Arkitektur

```mermaid
flowchart TB
  boot[platform-bootstrap]
  reggie[Reggie]
  cli["river CLI"]
  boot --> reggie
  cli -->|deploy customer-component| cust[CustomerService name=customer]
  cli -->|deploy order-component| backends["OrderBackend x3 name=order-backend"]
  cli -->|deploy order-component| router[OrderRouter name=order]
  client[OrderClient] -->|place| router
  router -->|"HRW på @Routing customerId"| backends
  backends -->|CustomerService plus Name customer| cust
  cust --> reggie
  backends --> reggie
  router --> reggie
```

Mer om flödet finns i [docs/arkitektur.md](docs/arkitektur.md).

## Så här kör du

Kräver JDK 25 eller senare, och läsrättighet till paketen på `https://maven.pkg.github.com/GradinIT/GradinITRiver`.

1. Lägg en server med id `github` i `~/.m2/settings.xml`. Lösenordet är en token med `read:packages`. Se [docs/beroenden.md](docs/beroenden.md).

2. Bygg och kör enhetstesterna. `OrderPlatformIT` är avstängt tills `platform-bootstrap` publicerar `PlatformMain`:

   ```bash
   ./mvnw -B -U verify
   ```

3. När `PlatformMain` finns i den publicerade SNAPSHOT körs samma flöde som en demo:

   ```bash
   ./scripts/run-demo.sh
   ```

JVM-flaggorna `--patch-module java.rmi=...` och `--add-exports java.rmi/java.rmi.activation=ALL-UNNAMED` sätts av `scripts/river-jvm-flags.sh`. Steg för steg, inklusive kommandona för hand, finns i [docs/korning.md](docs/korning.md).

Versionen är pinnad med `gradinit.river.version` i `pom.xml`. Paketrepot, BOM-importen och den lokala token-konfigurationen beskrivs i [docs/beroenden.md](docs/beroenden.md).

## Moduler

| Modul | Innehåll |
| --- | --- |
| `customer-api` | `CustomerService` |
| `customer-component` | en instans, `META-INF/SLA.xml`, `META-INF/customer_conf.xml` |
| `order-api` | `OrderService` och `OrderRequest` med `@Routing` på `customerId` |
| `order-component` | tre backendar och en router, `META-INF/order_conf.xml` |
| `client` | slår upp `OrderService` + `Name("order")` |
| `example-support` | discovery, lookup och `ServiceExporter.joinAnnotated` |
| `integration-tests` | `OrderPlatformIT` |

## Dokumentation

Filer i det här repot:

- [README.md](README.md)
- [docs/arkitektur.md](docs/arkitektur.md)
- [docs/beroenden.md](docs/beroenden.md)
- [docs/korning.md](docs/korning.md)
- [docs/kanda-problem.md](docs/kanda-problem.md)

Plattformens dokumentation på `develop`:

- [GradinITRiver README](https://github.com/GradinIT/GradinITRiver/blob/develop/README.md)
- [docs/plattform.md](https://github.com/GradinIT/GradinITRiver/blob/develop/docs/plattform.md)
- [docs/exempel-tjanst.md](https://github.com/GradinIT/GradinITRiver/blob/develop/docs/exempel-tjanst.md)
- [docs/supervisor.md](https://github.com/GradinIT/GradinITRiver/blob/develop/docs/supervisor.md)
- [docs/komponent-deploy-analys.md](https://github.com/GradinIT/GradinITRiver/blob/develop/docs/komponent-deploy-analys.md)

Begränsningar, bland annat att paketen kräver en PAT med `read:packages` eftersom GradinIT är ett personligt konto, står i [docs/kanda-problem.md](docs/kanda-problem.md).
