#!/usr/bin/env bash
# Skriver JVM-flaggor som GradinITRiver kräver på JDK 17+ (java.rmi.activation
# togs bort ur plattformen). Anropas som: eval "$(./scripts/river-jvm-flags.sh)"
set -euo pipefail

VERSION="${GRADINIT_RIVER_VERSION:-3.0.0-gradinit}"
REPO="${M2_REPO:-${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}}"
JAR="$REPO/se/gradinit/river/compat-rmi-activation/${VERSION}/compat-rmi-activation-${VERSION}.jar"

if [[ ! -f "$JAR" ]]; then
  echo "Hittar inte $JAR. Kör scripts/install-gradinit-river.sh först." >&2
  exit 1
fi

printf '%s\n' "--patch-module" "java.rmi=${JAR}" "--add-exports" "java.rmi/java.rmi.activation=ALL-UNNAMED"
