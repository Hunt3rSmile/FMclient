#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

if ! command -v gradle &>/dev/null; then
  echo "[!] Gradle not found. Install it: sudo pacman -S gradle"
  exit 1
fi

echo "[*] Generating Gradle wrapper..."
gradle wrapper --gradle-version 7.6 --distribution-type bin

echo "[*] Building fmvisuals mod..."
./gradlew build

JAR=$(find build/libs -name "fmvisuals-*.jar" ! -name "*-sources.jar" | head -1)
echo "[+] Built: $JAR"
echo "[*] Copying to dist/..."
cp "$JAR" ../dist/fmvisuals-1.0.0.jar
echo "[+] Done! Place dist/fmvisuals-1.0.0.jar in your .minecraft/mods/ folder."
