#!/usr/bin/env bash
# Startar plattformen från gradinit-river-dist, deployar customer och order,
# anropar klienten, startar webbkonsolen och undeployar.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/target/demo-logs"
mkdir -p "$LOGS"

echo "Bygger exemplet och packar upp gradinit-river-dist"
(cd "$ROOT" && ./mvnw -B -U -DskipITs package)

HOME_FILE="$ROOT/integration-tests/target/river-dist-home.txt"
if [[ ! -s "$HOME_FILE" ]]; then
  echo "Saknar $HOME_FILE" >&2
  exit 1
fi
DIST="$(tr -d '\n' < "$HOME_FILE")"
PLATFORM="$DIST/bin/river-platform"
RIVER="$DIST/bin/river"
CONSOLE="$DIST/bin/river-web-console"
for script in "$PLATFORM" "$RIVER" "$CONSOLE"; do
  if [[ ! -e "$script" ]]; then
    echo "Saknar $script" >&2
    exit 1
  fi
  chmod u+x "$script" || true
done

JAVA="${JAVA_HOME:-}/bin/java"
if [[ ! -x "$JAVA" ]]; then
  JAVA="$(command -v java)"
fi
mapfile -t FLAGS < <("$ROOT/scripts/river-jvm-flags.sh")

CUSTOMER="$ROOT/customer-component/target/customer-component-1.0.0.jar"
ORDER="$ROOT/order-component/target/order-component-1.0.0.jar"
CLIENT="$ROOT/client/target/client-1.0.0.jar"
CP="$(cat "$ROOT/client/target/classpath.txt")"

BOOT_PID=""
CONSOLE_PID=""

kill_tree() {
  local pid="$1"
  if [[ -z "$pid" ]]; then
    return
  fi
  local kid
  for kid in $(pgrep -P "$pid" || true); do
    kill_tree "$kid"
  done
  kill "$pid" 2>/dev/null || true
}

cleanup() {
  if [[ -n "${CONSOLE_PID:-}" ]]; then
    kill_tree "$CONSOLE_PID"
    wait "$CONSOLE_PID" 2>/dev/null || true
  fi
  if [[ -n "${BOOT_PID:-}" ]]; then
    kill_tree "$BOOT_PID"
    wait "$BOOT_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

run_script() {
  local script="$1"
  shift
  if [[ -x "$script" ]]; then
    "$script" "$@"
  else
    bash "$script" "$@"
  fi
}

echo "Startar bin/river-platform --clean"
run_script "$PLATFORM" --clean >"$LOGS/platform.log" 2>&1 &
BOOT_PID=$!

locator=""
for _ in $(seq 1 120); do
  if [[ -f "$LOGS/platform.log" ]]; then
    line="$(grep -E 'RIVER_PLATFORM_READY[[:space:]]+jini://' "$LOGS/platform.log" | head -n 1 || true)"
    locator="$(printf '%s\n' "$line" | grep -oE 'jini://[^[:space:]]+' | head -n 1 || true)"
    locator="${locator%.}"
    case "$locator" in
      jini://0.0.0.0:*) locator="jini://127.0.0.1:${locator#jini://0.0.0.0:}" ;;
    esac
    if [[ -n "$locator" ]]; then
      break
    fi
  fi
  if ! kill -0 "$BOOT_PID" 2>/dev/null; then
    echo "bin/river-platform avslutades innan RIVER_PLATFORM_READY" >&2
    cat "$LOGS/platform.log" >&2 || true
    exit 1
  fi
  sleep 1
done
if [[ -z "$locator" ]]; then
  echo "Timeout. Ingen RIVER_PLATFORM_READY i $LOGS/platform.log" >&2
  cat "$LOGS/platform.log" >&2 || true
  exit 1
fi
echo "Lookup $locator"
export JAVA_TOOL_OPTIONS="-Dse.gradinit.river.lookup=${locator}"

river() {
  echo "+ river $*"
  run_script "$RIVER" "$@"
}

river deploy "$CUSTOMER"
river deploy "$ORDER"
river list || true
river status || true
river monitor >"$LOGS/monitor.log" 2>&1 &
MONITOR_PID=$!
sleep 5
kill_tree "$MONITOR_PID"
wait "$MONITOR_PID" 2>/dev/null || true

echo "Startar bin/river-web-console"
run_script "$CONSOLE" >"$LOGS/web-console.log" 2>&1 &
CONSOLE_PID=$!
sleep 3
if kill -0 "$CONSOLE_PID" 2>/dev/null; then
  echo "Webbkonsolen kör. Logg: $LOGS/web-console.log"
else
  console_status=0
  wait "$CONSOLE_PID" || console_status=$?
  echo "bin/river-web-console avslutades (status ${console_status}). Logg:" >&2
  cat "$LOGS/web-console.log" >&2 || true
  CONSOLE_PID=""
  if [[ "$console_status" -ne 0 ]]; then
    exit 1
  fi
fi

echo "Anropar klienten"
"$JAVA" "${FLAGS[@]}" -Dse.gradinit.river.lookup="$locator" \
  -cp "$CLIENT:$CP" se.gradinit.riverexample.client.OrderClient alice SKU-100 1

if ! river undeploy order; then
  river undeploy "$ORDER"
fi
if ! river undeploy customer; then
  river undeploy "$CUSTOMER"
fi

echo "Klart. Loggar ligger i $LOGS"
