#!/usr/bin/env bash
# Startar plattformen, deployar customer och order, anropar klienten, kör monitor och undeployar.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${GRADINIT_RIVER_VERSION:-3.0.0-gradinit-SNAPSHOT}"
REPO="${M2_REPO:-${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}}"
RIVER="$REPO/se/gradinit/river"
LOGS="$ROOT/target/demo-logs"
mkdir -p "$LOGS"

jar_of() {
  local artifact="$1"
  local dir="$RIVER/${artifact}/${VERSION}"
  local newest=""
  local newest_time=0
  shopt -s nullglob
  for jar in "$dir"/${artifact}-*.jar; do
    case "$jar" in
      *-sources.jar|*-javadoc.jar|*-tests.jar) continue ;;
    esac
    local mtime
    if mtime=$(stat -c %Y "$jar" 2>/dev/null); then
      :
    else
      mtime=$(stat -f %m "$jar")
    fi
    if (( mtime > newest_time )); then
      newest="$jar"
      newest_time=$mtime
    fi
  done
  if [[ -z "$newest" ]]; then
    echo "Saknar ${artifact} ${VERSION} under ${dir}." >&2
    echo "Konfigurera GitHub Packages (server-id github) och kör ./mvnw -B -U package. Se docs/beroenden.md." >&2
    exit 1
  fi
  printf '%s\n' "$newest"
}

mapfile -t FLAGS < <("$ROOT/scripts/river-jvm-flags.sh")
JAVA="${JAVA_HOME:-}/bin/java"
if [[ ! -x "$JAVA" ]]; then
  JAVA="$(command -v java)"
fi

BOOTSTRAP="$(jar_of platform-bootstrap)"
CLI="$(jar_of platform-cli)"

main_class() {
  local jar="$1"
  local classpath="$2"
  python3 - "$jar" "$classpath" << 'PY'
import sys, zipfile
jar, classpath = sys.argv[1], sys.argv[2]

def manifest_main(path):
    try:
        with zipfile.ZipFile(path) as zf:
            text = zf.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
    except KeyError:
        return None
    for line in text.replace("\r", "").split("\n"):
        if line.startswith("Main-Class:"):
            return line.split(":", 1)[1].strip()
    return None

def has_main(data):
    return b"main" in data and b"([Ljava/lang/String;)V" in data and data[:4] == b"\xca\xfe\xba\xbe"

def mains(path):
    found = []
    with zipfile.ZipFile(path) as zf:
        for name in zf.namelist():
            if name.endswith(".class") and "$" not in name and not name.startswith("META-INF/versions/"):
                if has_main(zf.read(name)):
                    found.append(name[:-6].replace("/", "."))
    return found

chosen = manifest_main(jar) or ""
if not chosen:
    own = mains(jar)
    chosen = next(iter(own), "")
if not chosen:
    for entry in classpath.split(":"):
        if not entry.endswith(".jar"):
            continue
        try:
            names = mains(entry)
        except OSError:
            continue
        for name in names:
            if name.startswith("se.gradinit.river.platform.bootstrap.") or name == "com.sun.jini.start.ServiceStarter":
                chosen = name
                break
        if chosen:
            break
if not chosen:
    sys.exit("Ingen startklass i " + jar)
print(chosen)
PY
}

echo "Bygger exemplet"
(cd "$ROOT" && ./mvnw -B -U -DskipITs package)

PLATFORM_CP_FILE="$ROOT/integration-tests/target/platform-classpath.txt"
if [[ ! -s "$PLATFORM_CP_FILE" ]]; then
  echo "Saknar $PLATFORM_CP_FILE" >&2
  exit 1
fi
PLATFORM_CP="$(tr -d '\n' < "$PLATFORM_CP_FILE")"
BOOT_MAIN="$(main_class "$BOOTSTRAP" "$PLATFORM_CP")"
CLI_MAIN="$(main_class "$CLI" "$PLATFORM_CP")"

CUSTOMER="$ROOT/customer-component/target/customer-component-1.0.0.jar"
ORDER="$ROOT/order-component/target/order-component-1.0.0.jar"
CLIENT="$ROOT/client/target/client-1.0.0.jar"
CP="$(cat "$ROOT/client/target/classpath.txt")"

cleanup() {
  if [[ -n "${BOOT_PID:-}" ]] && kill -0 "$BOOT_PID" 2>/dev/null; then
    kill "$BOOT_PID" || true
    wait "$BOOT_PID" || true
  fi
}
trap cleanup EXIT

echo "Startar platform-bootstrap ($BOOT_MAIN)"
"$JAVA" "${FLAGS[@]}" -cp "$PLATFORM_CP" "$BOOT_MAIN" >"$LOGS/bootstrap.log" 2>&1 &
BOOT_PID=$!
sleep 15

river() {
  echo "+ river $*"
  "$JAVA" "${FLAGS[@]}" -cp "$PLATFORM_CP" "$CLI_MAIN" "$@"
}

river deploy "$CUSTOMER"
river deploy "$ORDER"
river list || true
river status || true
river monitor

echo "Anropar klienten"
"$JAVA" "${FLAGS[@]}" -cp "$CLIENT:$CP" se.gradinit.riverexample.client.OrderClient alice SKU-100 1

if ! river undeploy order; then
  river undeploy "$ORDER"
fi
if ! river undeploy customer; then
  river undeploy "$CUSTOMER"
fi

echo "Klart. Loggar ligger i $LOGS"
