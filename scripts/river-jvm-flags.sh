#!/usr/bin/env bash
# Skriver JVM-flaggor som GradinITRiver kräver på JDK 17+ (java.rmi.activation
# togs bort ur plattformen). Anropas som: eval "$(./scripts/river-jvm-flags.sh)"
set -euo pipefail

VERSION="${GRADINIT_RIVER_VERSION:-3.0.0-gradinit-SNAPSHOT}"
REPO="${M2_REPO:-${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}}"
DIR="$REPO/se/gradinit/river/compat-rmi-activation/${VERSION}"

newest=""
newest_time=0
shopt -s nullglob
for jar in "$DIR"/compat-rmi-activation-*.jar; do
  case "$jar" in
    *-sources.jar|*-javadoc.jar|*-tests.jar) continue ;;
  esac
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
  echo "Hittar inte compat-rmi-activation ${VERSION} under ${DIR}." >&2
  echo "Konfigurera GitHub Packages (server-id github) i ~/.m2/settings.xml och lös beroendet. Se docs/beroenden.md." >&2
  exit 1
fi

printf '%s\n' "--patch-module" "java.rmi=${newest}" "--add-exports" "java.rmi/java.rmi.activation=ALL-UNNAMED"
