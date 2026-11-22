#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${LAB_MC20:?Set LAB_MC20 to the absolute Fabric server fixture path}"
java="${JAVA_HOME:-${LAB_MC20}/../jdk17}"
classpath="${LAB_MC20}/mods/create.jar"
while IFS= read -r -d '' file; do classpath="$classpath:$file"; done   < <(find "$LAB_MC20/libraries" "$LAB_MC20/.fabric/remappedJars" "$LAB_MC20/.fabric/processedMods" -name '*.jar' -print0)
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
sources=(EngineeringSite.java CreateReadings.java CreateSettings.java PonderGuide.java)
if [[ -n ${LAB_VS_MODS:-} ]]; then
    classpath="$classpath:$LAB_VS_MODS/vs.jar:$LAB_VS_MODS/eureka.jar"
    unzip -p "$LAB_VS_MODS/vs.jar" 'META-INF/jars/fabric-language-kotlin-*.jar' > "$scratch/kotlin.jar"
    unzip -j "$scratch/kotlin.jar" 'META-INF/jars/kotlin-stdlib-*.jar' -d "$scratch" >/dev/null
    for file in "$scratch"/kotlin-stdlib-*.jar; do classpath="$classpath:$file"; done
    sources+=(../ship-bridge/LabShips.java ../ship-bridge/EngineeringShips.java)
fi
"$java/bin/javac" --release 17 -proc:none -cp "$classpath" -d "$scratch" "${sources[@]}"
mkdir -p "$scratch/engineering/ponder"
cp ../../ponder/knowledge/scenes.jsonl ../../ponder/THIRD-PARTY.md "$scratch/engineering/ponder/"
"$java/bin/jar" --create --file game-bridge.jar -C "$scratch" lab -C "$scratch" engineering fabric.mod.json
