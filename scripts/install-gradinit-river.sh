#!/usr/bin/env bash
# Installerar GradinITRiver (develop) i det lokala Maven-repot.
# Artefakterna publiceras inte till Maven Central. Exemplet beror på
# groupId se.gradinit.river, versionen i ../pom.xml (gradinit.river.version).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UPSTREAM_URL="${GRADINIT_RIVER_URL:-https://github.com/GradinIT/GradinITRiver.git}"
UPSTREAM_REF="${GRADINIT_RIVER_REF:-develop}"
DEST="${GRADINIT_RIVER_SRC:-"$ROOT/.upstream/GradinITRiver"}"

if [[ ! -x "$DEST/mvnw" ]]; then
  mkdir -p "$(dirname "$DEST")"
  echo "Klonar $UPSTREAM_URL ($UPSTREAM_REF) till $DEST"
  git clone --branch "$UPSTREAM_REF" --single-branch "$UPSTREAM_URL" "$DEST"
else
  echo "Uppdaterar $DEST till $UPSTREAM_REF"
  git -C "$DEST" fetch origin "$UPSTREAM_REF"
  git -C "$DEST" checkout "$UPSTREAM_REF"
  git -C "$DEST" pull --ff-only origin "$UPSTREAM_REF"
fi

echo "Bygger och installerar GradinITRiver (tester överhoppas)"
(cd "$DEST" && ./mvnw -B install -DskipTests)

echo "Installerat. Förväntad version: se.gradinit.river:*:3.0.0-gradinit"
echo "Kontrollera att den stämmer med gradinit.river.version i $ROOT/pom.xml"
