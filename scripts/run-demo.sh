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

echo "Bygger exemplet"
(cd "$ROOT" && ./mvnw -B -U -DskipITs package)

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

echo "Startar platform-bootstrap"
"$JAVA" "${FLAGS[@]}" -jar "$BOOTSTRAP" >"$LOGS/bootstrap.log" 2>&1 &
BOOT_PID=$!
sleep 15

river() {
  echo "+ river $*"
  "$JAVA" "${FLAGS[@]}" -jar "$CLI" "$@"
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
