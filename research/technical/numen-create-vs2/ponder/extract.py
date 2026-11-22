"""Extract machine knowledge from decompiled Create Ponder scene scripts.

Each scene becomes one record: title, ordered teaching steps, the actions the
scene performs, and every simulated value it injects. Simulated values are
tagged so they are not mistaken for real game mechanics.
"""
import json
import os
import re
from pathlib import Path

SRC = Path(r"D:\NumenLab\ponder\all")
NBT_JAR_INDEX = Path(r"D:\NumenLab\ponder\nbt-index.json")
OUT = Path(r"D:\NumenLab\ponder\knowledge")

METHOD_RE = re.compile(r"public static void (\w+)\(SceneBuilder builder, SceneBuildingUtil util\) \{")
TITLE_RE = re.compile(r'scene\.title\("([^"]*)",\s*"([^"]*)"\)')
TEXT_RE = re.compile(r'\.text\("((?:[^"\\]|\\.)*)"\)')
SHOWTEXT_RE = re.compile(r'showText\((\d+)\)')
IDLE_RE = re.compile(r"scene\.idle\((\d+)\)")
SPEED_RE = re.compile(r"setKineticSpeed\(([^,]{0,90}?),\s*(-?[\d.]+)f?\)")
CALL_RE = re.compile(r"scene\.(?:world|effects|overlay|configureBasePlate|showBasePlate|rotateCameraY|idle)\(\)?\.?(\w+)?\(")
POS_RE = re.compile(r"fromTo\((\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+)\)")
ITEM_RE = re.compile(r"class_1802\.field_(\d+)")
RECIPE_ITEM_RE = re.compile(r'"minecraft:([a-z_]+)"')


def brace_slice(text, open_index):
    depth = 0
    for i in range(open_index, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[open_index + 1:i]
    return text[open_index + 1:]


def parse_body(body):
    events = []
    # walk the body once, remembering which construct each match belongs to
    for match in re.finditer(
        r"showText\((\d+)\)|\.text\(\"(?P<text>(?:[^\"\\]|\\.)*)\"\)|scene\.idle\((?P<idle>\d+)\)|"
        r"setKineticSpeed\((?P<sel>[^,]{0,90}?),\s*(?P<speed>-?[\d.]+)f?\)|"
        r"indicateSuccess\(|indicateRedstone\(|showControls\(|withItem\(|createItemEntity\(|"
        r"modifyBlockEntity\(|rotateCameraY\(|showSection\(|showIndependentSection\(",
        body,
    ):
        token = match.group(0)
        if token.startswith("showText"):
            events.append({"kind": "text", "hold": int(match.group(1))})
        elif token.startswith(".text"):
            text = match.group("text").encode().decode("unicode_escape", "replace")
            if events and events[-1]["kind"] == "text" and "text" not in events[-1]:
                events[-1]["text"] = text
            else:
                events.append({"kind": "text", "text": text})
        elif token.startswith("scene.idle"):
            events.append({"kind": "idle", "ticks": int(match.group("idle"))})
        elif token.startswith("setKineticSpeed"):
            events.append({
                "kind": "simulated_speed",
                "selection": match.group("sel").strip(),
                "rpm": float(match.group("speed")),
            })
        else:
            name = token.split("(")[0]
            events.append({"kind": "action", "call": name})
    return events


METHOD_HINT = [None]



ALIASES = {
    "windmill_source": "windmill_bearing/source.nbt",
    "windmill_structure": "windmill_bearing/structure.nbt",
    "bearing_modes": "mechanical_bearing/modes.nbt",
    "belt_casing": "belt/encasing.nbt",
    "chain_gearshift": "chain_drive/gearshift.nbt",
    "linear_chassis_group": "chassis/linear_group.nbt",
    "linear_chassis_attachment": "chassis/linear_attachment.nbt",
    "funnel_direction": "funnels/direction.nbt",
    "shaft_casing": "shaft/encasing.nbt",
    "cogwheel": "cog/small.nbt",
    "cogwheel_casing": "cog/encasing.nbt",
    "rotation_speed_controller": "speed_controller.nbt",
    "portable_storage_interface": "portable_interface/transfer.nbt",
    "portable_storage_interface_redstone": "portable_interface/redstone.nbt",
    "mechanical_roller_pave": "mechanical_roller/clear_and_pave.nbt",
}


def resolve_nbt(scene_id, index):
    if scene_id in ALIASES:
        return ALIASES[scene_id]

    if scene_id in index:
        return index[scene_id]
    if METHOD_HINT[0]:
        suffix = METHOD_HINT[0]
        for key in index:
            if key == suffix or key.endswith("_" + suffix) or key.endswith("/" + suffix):
                return index[key]
    best = None
    for key in index:
        if key.startswith(scene_id) or scene_id.startswith(key):
            score = min(len(key), len(scene_id))
            if best is None or score > best[0]:
                best = (score, key)
    return index[best[1]] if best else None


def extract():
    records = []
    nbt_index = json.loads(NBT_JAR_INDEX.read_text(encoding="utf-8-sig")) if NBT_JAR_INDEX.exists() else {}
    for path in sorted(SRC.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for m in METHOD_RE.finditer(text):
            method = m.group(1)
            body = brace_slice(text, m.end() - 1)
            title = TITLE_RE.search(body)
            scene_id = title.group(1) if title else method
            record = {
                "id": scene_id,
                "nbt_resolved": None,
                "method": method,
                "class": path.stem,
                "title": title.group(2) if title else None,
                "nbt": nbt_index.get(scene_id),
                "steps": parse_body(body),
            }
            METHOD_HINT[0] = method
            record["nbt"] = resolve_nbt(scene_id, nbt_index)
            record["simulated_count"] = sum(1 for s in record["steps"] if s["kind"] == "simulated_speed")
            record["text_count"] = sum(1 for s in record["steps"] if s["kind"] == "text" and s.get("text"))
            records.append(record)
    return records


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    records = extract()
    with (OUT / "scenes.jsonl").open("w", encoding="utf-8") as fh:
        for record in records:
            fh.write(json.dumps(record, ensure_ascii=False) + "\n")
    ids = sorted({r["id"] for r in records})
    (OUT / "index.json").write_text(json.dumps({
        "count": len(records),
        "ids": ids,
        "with_text": sum(1 for r in records if r["text_count"]),
        "with_simulated_speed": sum(1 for r in records if r["simulated_count"]),
    }, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({"records": len(records), "unique_ids": len(ids)}, ensure_ascii=False))

