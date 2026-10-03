#!/usr/bin/env bash
# Kör ett kommando så att både den processen och barn-JVM:er får activation-patchen en gång.
# bin/river-platform sätter ofta --patch-module på sin egen java. Barnprocesser ärver inte
# de flaggorna, men de ärver JDK_JAVA_OPTIONS. En java först i PATH tar bort dubbletter
# från kommandoraden så plattformens JVM inte patchar java.rmi två gånger.
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "användning: with-river-jvm.sh kommando [argument...]" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mapfile -t FLAGS < <("$ROOT/scripts/river-jvm-flags.sh")
export JDK_JAVA_OPTIONS="${FLAGS[*]}${JDK_JAVA_OPTIONS:+ ${JDK_JAVA_OPTIONS}}"

REAL_JAVA="${JAVA_HOME:-}/bin/java"
if [[ ! -x "$REAL_JAVA" ]]; then
  REAL_JAVA="$(command -v java)"
fi
export RIVER_REAL_JAVA="$REAL_JAVA"

WRAP="$(mktemp -d)"
cat >"$WRAP/java" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
real="${RIVER_REAL_JAVA:?}"
out=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --patch-module)
      if [[ "${2:-}" == java.rmi=* ]]; then
        shift 2
        continue
      fi
      out+=("$1" "${2:-}")
      shift 2
      ;;
    --add-exports)
      if [[ "${2:-}" == java.rmi/* ]]; then
        shift 2
        continue
      fi
      out+=("$1" "${2:-}")
      shift 2
      ;;
    *)
      out+=("$1")
      shift
      ;;
  esac
done
exec "$real" "${out[@]}"
EOF
chmod +x "$WRAP/java"
export PATH="$WRAP:${PATH:-}"
if [[ -x "$1" ]]; then
  exec "$@"
fi
script="$1"
shift
exec bash "$script" "$@"
