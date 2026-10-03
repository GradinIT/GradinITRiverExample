#!/usr/bin/env bash
# Hämtar se.gradinit.river:gradinit-river-dist och packar upp bin/river-platform, bin/river
# och bin/river-web-console. Skriver katalogens sökväg till
# integration-tests/target/river-dist-home.txt.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-${GRADINIT_RIVER_VERSION:-3.0.0-gradinit-SNAPSHOT}}"
MODULE="$ROOT/integration-tests"
ARCHIVE="$MODULE/target/river-dist-archive"
UNPACK="$MODULE/target/river-dist-unpacked"
HOME_FILE="$MODULE/target/river-dist-home.txt"
FETCH_LOG="$MODULE/target/river-dist-fetch.log"

mkdir -p "$MODULE/target"
rm -rf "$ARCHIVE" "$UNPACK" "$HOME_FILE"
mkdir -p "$UNPACK"
: >"$FETCH_LOG"

MVN=(
  "$ROOT/mvnw" -B -U -ntp -N -f "$ROOT/pom.xml"
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy
  -DoverWriteSnapshots=true
  -DoutputDirectory="$ARCHIVE"
)

attempts=(
  "se.gradinit.river:gradinit-river-dist:${VERSION}:zip:dist"
  "se.gradinit.river:gradinit-river-dist:${VERSION}:tar.gz:dist"
  "se.gradinit.river:gradinit-river-dist:${VERSION}:zip"
  "se.gradinit.river:gradinit-river-dist:${VERSION}:tar.gz"
  "se.gradinit.river:gradinit-river-dist:${VERSION}:zip:bin"
  "se.gradinit.river:gradinit-river-dist:${VERSION}:tar.gz:bin"
  "se.gradinit.river:gradinit-river-dist:${VERSION}:zip:assembly"
  "se.gradinit.river:gradinit-river-dist:${VERSION}:tgz:dist"
)

found=""
for artifact in "${attempts[@]}"; do
  rm -rf "$ARCHIVE"
  mkdir -p "$ARCHIVE"
  echo "=== $artifact" | tee -a "$FETCH_LOG"
  if "${MVN[@]}" -Dartifact="$artifact" >>"$FETCH_LOG" 2>&1; then
    found="$(find "$ARCHIVE" -type f \
      \( -name '*.zip' -o -name '*.tar.gz' -o -name '*.tgz' -o -name '*.jar' \) \
      ! -name '*-sources.*' ! -name '*-javadoc.*' ! -name '*-tests.*' -print -quit)"
    if [[ -n "$found" ]]; then
      echo "Hämtade $found" | tee -a "$FETCH_LOG"
      break
    fi
  fi
  found=""
done

if [[ -z "$found" ]]; then
  echo "Kunde inte hämta gradinit-river-dist ${VERSION}." >&2
  echo "Försökte: ${attempts[*]}" >&2
  echo "Logg: $FETCH_LOG" >&2
  tail -n 80 "$FETCH_LOG" >&2 || true
  exit 1
fi

case "$found" in
  *.tar.gz|*.tgz) tar -xzf "$found" -C "$UNPACK" ;;
  *) unzip -q "$found" -d "$UNPACK" ;;
esac

home=""
while IFS= read -r script; do
  bindir="$(dirname "$script")"
  if [[ "$(basename "$bindir")" == "bin" && -e "$bindir/river" && -e "$bindir/river-web-console" ]]; then
    home="$(cd "$(dirname "$bindir")" && pwd)"
    break
  fi
done < <(find "$UNPACK" \( -type f -o -type l \) -name 'river-platform')

if [[ -z "$home" ]]; then
  echo "Arkivet $found saknar bin/river-platform, bin/river och bin/river-web-console." >&2
  find "$UNPACK" -print | head -n 200 >&2
  exit 1
fi

chmod -R u+rx "$home/bin" || true
printf '%s\n' "$home" >"$HOME_FILE"
echo "gradinit-river-dist hem: $home"
