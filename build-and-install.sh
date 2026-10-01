#!/usr/bin/env bash
# MlumInventory - build and install (Linux / macOS)
# Usage: ./build-and-install.sh [path/to/instance/mods]
set -euo pipefail

cd "$(dirname "$0")"

echo
echo " ============================================"
echo "  MLUM INVENTORY  -  build & install"
echo " ============================================"
echo

if ! command -v java >/dev/null 2>&1; then
    echo " [X] No Java found. Install JDK 17:"
    echo "     https://adoptium.net/temurin/releases/?version=17"
    exit 1
fi

echo " [1/3] Java: $(command -v java)"
java -version 2>&1 | head -1
echo

echo " [2/3] Building (first run downloads Gradle + Forge, 5-15 min)..."
./gradlew build --console=plain

JAR="$(find build/libs -name 'mlum-*.jar' ! -name '*sources*' ! -name '*dev*' | head -1)"
if [[ -z "$JAR" ]]; then
    echo " [X] Build succeeded but no jar found in build/libs."
    exit 1
fi
echo
echo " Built: $JAR"

MODS="${1:-}"
if [[ -z "$MODS" ]]; then
    for candidate in \
        "$HOME/curseforge/minecraft/Instances/claude/mods" \
        "$HOME/Documents/curseforge/minecraft/Instances/claude/mods" \
        "$HOME/.minecraft/mods"; do
        [[ -d "$candidate" ]] && MODS="$candidate" && break
    done
fi

if [[ -z "$MODS" || ! -d "$MODS" ]]; then
    echo
    echo " [!] No mods folder found. Copy it yourself:"
    echo "       $JAR"
    exit 0
fi

echo " [3/3] Installing to $MODS"
rm -f "$MODS"/mlum-*.jar
cp "$JAR" "$MODS/"

echo
echo " Done. Launch the pack and press E."
