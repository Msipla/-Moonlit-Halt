"""Extract compact engineering cases from the official Create Ponder scene structures.

Source: assets/create/ponder/**/*.nbt inside the installed Create 6.0.8.1 jar (MIT).
Only device blocks are kept; coordinates are relative to an anchor device.
"""
import json
import sys
import zipfile
from pathlib import Path

import nbtlib

JAR = Path(r"D:\NumenLab\mc20\mods\create-fabric-6.0.8.1+build.1744-mc1.20.1.jar")
OUT = Path(r"D:\NumenLab\acceptance\cases\cases.json")

SCENES = [
    "millstone",
    "mechanical_press/pressing",
    "mechanical_press/compacting",
    "fan/processing",
    "chute/smart",
    "mechanical_mixer/mixing",
]

DEVICE_PRIORITY = [
    "create:millstone",
    "create:mechanical_press",
    "create:mechanical_mixer",
    "create:encased_fan",
    "create:chute",
    "create:smart_chute",
]

DEVICE_MATCH = (
    "create:millstone",
    "create:mechanical_press",
    "create:mechanical_mixer",
    "create:encased_fan",
    "create:chute",
    "create:smart_chute",
    "create:funnel",
    "create:andesite_funnel",
    "create:brass_funnel",
    "create:andesite_belt_funnel",
    "create:brass_belt_funnel",
    "create:shaft",
    "create:cogwheel",
    "create:large_cogwheel",
    "create:gearbox",
    "create:creative_motor",
    "create:depot",
    "create:basin",
    "create:belt",
    "minecraft:barrel",
)

LIMIT = 24


def load_scene(name):
    with zipfile.ZipFile(JAR) as jar:
        raw = jar.read(f"assets/create/ponder/{name}.nbt")
    tmp = Path(r"D:\NumenLab\acceptance\cases\_tmp.nbt")
    tmp.write_bytes(raw)
    return nbtlib.load(str(tmp))


def props(entry):
    result = entry.get("Properties", {})
    return {str(k): str(v) for k, v in result.items()}


def extract(name):
    structure = load_scene(name)
    palette = structure["palette"]
    size = [int(v) for v in structure["size"]]
    blocks = []
    for block in structure["blocks"]:
        state = palette[int(block["state"])]
        block_id = str(state["Name"])
        if not any(block_id == prefix or block_id.endswith(prefix.split(":", 1)[1]) and block_id.startswith("create:")
                   for prefix in DEVICE_MATCH):
            continue
        pos = [int(v) for v in block["pos"]]
        blocks.append({"p": pos, "id": block_id, "props": props(state)})
    if not blocks:
        return None
    anchor = None
    for wanted in DEVICE_PRIORITY:
        for block in blocks:
            if block["id"] == wanted:
                anchor = block
                break
        if anchor:
            break
    if anchor is None:
        anchor = blocks[0]
    ax, ay, az = anchor["p"]
    compact = []
    for block in sorted(blocks, key=lambda b: (b["p"][1], b["p"][0], b["p"][2])):
        item = {"p": [block["p"][0] - ax, block["p"][1] - ay, block["p"][2] - az], "id": block["id"]}
        if block["props"]:
            item["props"] = block["props"]
        compact.append(item)
        if len(compact) >= LIMIT:
            break
    return {
        "scene": name,
        "anchor": anchor["id"],
        "size": size,
        "devices": compact,
        "source": "Create 6.0.8.1 assets/create/ponder/%s.nbt (MIT)" % name,
    }


def main():
    cases = {}
    for name in SCENES:
        try:
            case = extract(name)
        except KeyError:
            print("missing", name)
            continue
        if case:
            cases[name] = case
            print(name, len(case["devices"]), "devices")
    OUT.write_text(json.dumps(cases, ensure_ascii=False, indent=1), encoding="utf-8")
    print("wrote", OUT, OUT.stat().st_size, "bytes")


if __name__ == "__main__":
    sys.exit(main())
