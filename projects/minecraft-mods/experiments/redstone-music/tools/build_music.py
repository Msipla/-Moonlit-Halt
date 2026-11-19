"""Use Midicraft's existing planner to produce three vanilla redstone structures."""

import argparse
from collections import Counter
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory

import pretty_midi

from arrange import arrange, project
from schematic import load_sponge, save_template

UPSTREAM = "32925a2c51840260590f0286732d4cea44519787"


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def export(mid_file, output, title, checkout, mod, game, width=24):
    commit = subprocess.check_output(["git", "-C", checkout, "rev-parse", "HEAD"], text=True).strip()
    if commit != UPSTREAM:
        raise ValueError(f"Use the Midicraft source at {UPSTREAM}.")
    game_jar = game / "versions" / "26.3" / "26.3.jar"
    libraries = sorted((game / "libraries").rglob("*.jar"))
    if not game_jar.is_file() or not mod.is_file() or not libraries:
        raise ValueError("Expected the official 26.3 client JAR, its libraries, and Midicraft v1.0.0.")
    libraries = [lib for lib in libraries if "-natives-" not in lib.name]
    export_src = checkout / "src/test/java/com/midicraft/client/compat"
    if not 1 <= len(title.strip()) <= 80:
        raise ValueError("Use a title between 1 and 80 characters.")
    midi = pretty_midi.PrettyMIDI(str(mid_file))
    events, tracks = arrange(midi)
    preview = [event for event in events if event["tick"] < 100]
    if not preview:
        start = events[0]["tick"]
        preview = [dict(event, tick=event["tick"] - start) for event in events
                   if event["tick"] < start + 100]
    sounds = sorted({event["instrument"] for event in events})
    showcase = [{"track": index, "instrument": sound, "note": 12, "tick": index * 8,
                 "velocity": 100, "durationSeconds": .2} for index, sound in enumerate(sounds)]
    output.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix="redstone-music-") as temp_path:
        temporary = Path(temp_path)
        settings = temporary / "midicraft-settings.json"
        write_json(settings, {"chordThinUnder": 0, "thinChordsByVolume": False, "thinChordsByStrikes": False})
        classpath = os.pathsep.join(map(str, [temporary, game_jar, mod, *libraries]))
        java_home = os.environ.get("JAVA_HOME")
        java = str(Path(java_home) / "bin" / "java") if java_home else "java"
        javac = str(Path(java_home) / "bin" / "javac") if java_home else "javac"
        subprocess.run([javac, "-cp", classpath, "-d", temporary,
                        *(export_src / f"{name}.java" for name in ("ExportBuild", "GameSettings", "TestSongs"))],
                       cwd=temporary, check=True)
        combined = {}
        components = {}
        width_max = height_max = depth = 0
        parts = (("full", title, events, width, 2),
                 ("excerpt", "Excerpt", preview, min(width, 20), 1),
                 ("instruments", "Instruments", showcase, min(width, 20), 1))
        for name, label, notes, part_width, floors in parts:
            source = temporary / f"{name}.json"
            write_json(source, project(label, notes))
            export_args = temporary / "export-args.txt"
            export_args.write_text("\n".join(map(str, [
                source, "--out", temporary, "--name", name, "--mode", "interleaved",
                "--width", part_width, "--floors", floors, "--style", "plain",
            ])), encoding="utf-8")
            result = subprocess.run([
                java, "-XX:ActiveProcessorCount=4", "-cp", classpath, f"-Dgame.config={settings}",
                "com.midicraft.client.compat.ExportBuild", f"@{export_args}"],
                cwd=temporary, text=True, encoding="utf-8", stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT, check=True)
            print(result.stdout, flush=True)
            if "This build has faults" in result.stdout:
                raise ValueError(f"Midicraft found circuit faults in {name}.")
            (x_size, y_size, z_size), blocks = load_sponge(temporary / f"{name}.schem")
            placed = Counter((props["instrument"], int(props["note"])) for block, props, _ in blocks.values()
                             if block == "minecraft:note_block")
            planned = Counter((event["instrument"], event["note"]) for event in notes)
            if placed != planned:
                raise ValueError(f"{name} does not preserve the planned note counts: "
                                 f"missing={planned - placed}; extra={placed - planned}.")
            buttons = [(x, y, z + depth) for (x, y, z), (block, _, _) in blocks.items()
                       if block == "minecraft:oak_button"]
            if len(buttons) != 1:
                raise ValueError(f"{name} must have one start button.")
            components[name] = {"startButton": buttons[0], "offset": (0, 0, depth),
                                "size": (x_size, y_size, z_size), "onsets": len(notes)}
            combined.update({(x, y, z + depth): block for (x, y, z), block in blocks.items()})
            width_max = max(width_max, x_size)
            height_max = max(height_max, y_size)
            depth += z_size + 3
        size = (width_max, height_max, depth - 3)
        if any(value > limit for value, limit in zip(size, (96, 64, 96))) or (
            math.prod(size) > 50_000 or len(combined) > 12_000
        ):
            raise ValueError("The merged structure exceeds the placement limits. Use a shorter excerpt.")

    structure = output / "music.nbt"
    save_template(structure, size, combined)
    write_json(output / "music.json", {
        "title": title, "instruments": sounds, "resourcePack": None,
        "structureSha256": hashlib.sha256(structure.read_bytes()).hexdigest(),
        "size": size, "blocks": len(combined), "components": components,
    })
    write_json(output / "arrangement.json", {"tracks": tracks, "events": events, "ticksPerSecond": 20})
    print(f"Vanilla 1.20.1: {structure} ({size}, {len(combined)} blocks).")


if __name__ == "__main__":
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("midi", type=Path, help="Intermediate MIDI written by transcribe.py")
    cli.add_argument("output", type=Path)
    cli.add_argument("--title", default="Night Relay")
    cli.add_argument("--midicraft-source", type=Path, required=True)
    cli.add_argument("--midicraft-jar", type=Path, required=True)
    cli.add_argument("--minecraft", type=Path, required=True, help="Clean Minecraft Java 26.3 installation")
    cli.add_argument("--width", type=int, choices=range(16, 41), metavar="16..40", default=24)
    args = cli.parse_args()
    export(args.midi.resolve(), args.output.resolve(), args.title, args.midicraft_source.resolve(),
           args.midicraft_jar.resolve(), args.minecraft.resolve(), args.width)
