#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${LAB_MC20:?Set LAB_MC20 to the absolute Fabric server fixture path}"
: "${LAB_VS_MODS:?Set LAB_VS_MODS to the absolute directory containing vs.jar and eureka.jar}"
java="${JAVA_HOME:-${LAB_MC20}/../jdk17}"
classpath="${LAB_VS_MODS}/vs.jar:${LAB_VS_MODS}/eureka.jar"
while IFS= read -r -d '' file; do classpath="$classpath:$file"; done   < <(find "$LAB_MC20/libraries" "$LAB_MC20/.fabric/remappedJars" "$LAB_MC20/.fabric/processedMods" -name '*.jar' -print0)
kotlin="${LAB_VS_MODS}/kotlin.jar"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
unzip -j "$kotlin" 'META-INF/jars/kotlin-stdlib-*.jar' -d "$scratch" >/dev/null
for file in "$scratch"/*.jar; do classpath="$classpath:$file"; done
mkdir -p "$scratch/classes"
"$java/bin/javac" --release 17 -cp "$classpath" -d "$scratch/classes" LabShips.java
"$java/bin/jar" --create --file ship-bridge.jar -C "$scratch/classes" lab fabric.mod.json
