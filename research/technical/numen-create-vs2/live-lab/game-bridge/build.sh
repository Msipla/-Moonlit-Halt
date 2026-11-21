#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${LAB_MC20:?Set LAB_MC20 to the absolute Fabric server fixture path}"
java="${JAVA_HOME:-${LAB_MC20}/../jdk17}"
classpath="${LAB_MC20}/mods/create.jar"
while IFS= read -r -d '' file; do classpath="$classpath:$file"; done   < <(find "$LAB_MC20/libraries" "$LAB_MC20/.fabric/remappedJars" "$LAB_MC20/.fabric/processedMods" -name '*.jar' -print0)
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
"$java/bin/javac" --release 17 -proc:none -cp "$classpath" -d "$scratch" EngineeringSite.java CreateReadings.java CreateSettings.java
"$java/bin/jar" --create --file game-bridge.jar -C "$scratch" lab fabric.mod.json
