"""Convert the tested Midicraft Sponge v3 subset to a Java 1.20.1 template."""

import json
import re
from pathlib import Path

import nbtlib as nbt

DATA_VERSION = 3465
PALETTE_NAME = re.compile(r"(minecraft:[a-z0-9_]+)(?:\[([a-z0-9_=,]+)\])?")
ALLOWED = set("""
air note_block redstone_wire repeater observer comparator
oak_button oak_wall_sign stone stone_slab redstone_block redstone_lamp
glass white_wool sand dirt oak_planks oak_slab clay gold_block packed_ice
bone_block iron_block soul_sand pumpkin emerald_block hay_block glowstone
piston sticky_piston piston_head tinted_glass
""".split())


def int_list(values):
    return nbt.List[nbt.Int](values)


def varints(data):
    result = []
    shift = value = 0
    for byte in data:
        byte = int(byte) & 255
        value |= (byte & 127) << shift
        if byte & 128:
            shift += 7
            if shift >= 35:
                raise ValueError("Invalid Sponge palette varint.")
        else:
            result.append(value)
            shift = value = 0
    if shift:
        raise ValueError("Truncated Sponge palette varint.")
    return result


def load_sponge(path: Path):
    root = nbt.load(path)["Schematic"]
    if root["Version"] != 3 or root.get("Entities"):
        raise ValueError("Expected a Sponge v3 schematic without entities.")
    size = width, height, length = tuple(int(root[axis]) for axis in ("Width", "Height", "Length"))
    palette = {}
    for state, index in root["Blocks"]["Palette"].items():
        match = PALETTE_NAME.fullmatch(state)
        if not match:
            raise ValueError(f"Unsupported block state: {state}")
        block, properties = match.groups()
        if block.removeprefix("minecraft:") not in ALLOWED:
            raise ValueError(f"Block outside the verified 1.20.1 palette: {block}")
        palette[int(index)] = (block, dict(p.split("=") for p in properties.split(",")) if properties else {})
    ids = varints(root["Blocks"]["Data"])
    if len(ids) != width * height * length or not set(ids) <= palette.keys():
        raise ValueError("Sponge palette count does not match dimensions.")

    signs = {}
    for entity in root["Blocks"].get("BlockEntities", []):
        if entity["Id"] != "minecraft:sign":
            raise ValueError(f"Unsupported block entity: {entity['Id']}")
        source = entity["Data"]["front_text"]["messages"]
        if len(source) != 4 or any(not isinstance(line, nbt.String) for line in source):
            raise ValueError("Only plain four-line Midicraft signs are supported.")
        messages = nbt.List[nbt.String](json.dumps({"text": str(line)}, ensure_ascii=False) for line in source)
        signs[tuple(int(p) for p in entity["Pos"])] = nbt.Compound(
            id=nbt.String("minecraft:sign"), front_text=nbt.Compound(messages=messages))

    blocks = {}
    for i, state in enumerate(ids):
        name, props = palette[state]
        if name != "minecraft:air":
            pos = i % width, i // (width * length), i // width % length
            blocks[pos] = (name, props, signs.get(pos))
    if any(pos not in blocks or blocks[pos][0] != "minecraft:oak_wall_sign" for pos in signs):
        raise ValueError("Sponge sign has no corresponding block.")
    return size, blocks


def save_template(path: Path, size, blocks):
    indices = {}
    records = []
    # Supports go in before most circuits. Template placement also performs
    # vanilla shape updates after the bulk write.
    for pos, (name, props, data) in sorted(blocks.items(), key=lambda pair: (pair[0][1], pair[0][0], pair[0][2])):
        key = name, tuple(sorted(props.items()))
        state = indices.setdefault(key, len(indices))
        block = nbt.Compound(pos=int_list(pos), state=nbt.Int(state))
        if data is not None:
            block["nbt"] = data
        records.append(block)

    palette = []
    for name, properties in indices:
        state = nbt.Compound(Name=nbt.String(name))
        if properties:
            state["Properties"] = nbt.Compound((key, nbt.String(value)) for key, value in properties)
        palette.append(state)

    template = nbt.File({
        "DataVersion": nbt.Int(DATA_VERSION), "size": int_list(size),
        "palette": nbt.List[nbt.Compound](palette), "blocks": nbt.List[nbt.Compound](records),
        "entities": nbt.List[nbt.Compound](),
    })
    template.save(path, gzipped=True)
