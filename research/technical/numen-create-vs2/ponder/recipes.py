"""Attach Create recipe data to Ponder scenes and publish a queryable index.

Recipe linkage is explicit per machine type: a scene about the Mechanical Press
gets data/create/recipes/pressing/*, the Millstone gets milling, the Mixer gets
mixing. Ponder scripts simulate outcomes; the recipes are the real data.
"""
import json
import re
import zipfile
from pathlib import Path

JAR = Path(r"D:\NumenLab\mc20\mods\create-fabric-6.0.8.1+build.1744-mc1.20.1.jar")
KNOWLEDGE = Path(r"D:\NumenLab\ponder\knowledge")

SCENE_RECIPE_TYPES = {
    "millstone": "milling",
    "mechanical_press": "pressing",
    "mechanical_mixer": "mixing",
    "basin": None,
    "crushing_wheel": "crushing",
    "deployer": "deploying",
    "mechanical_saw": "cutting",
    "encased_fan": "fan_processing",
    "spout": "filling",
    "item_drain": "emptying",
    "sandpaper": "sandpaper_polishing",
    "mechanical_crafter": "crafting",
    "compacting": "compacting",
}


def read_recipes():
    out = {}
    with zipfile.ZipFile(JAR) as jar:
        for name in jar.namelist():
            m = re.match(r"data/create/recipes/([a-z_]+)/([a-z0-9_/]+)\.json$", name)
            if not m:
                continue
            kind = m.group(1)
            try:
                data = json.loads(jar.read(name))
            except Exception:
                continue
            entry = {"id": m.group(2), "type": data.get("type")}
            if "results" in data:
                outs = data["results"]
                entry["output"] = outs[0].get("item") if outs and isinstance(outs[0], dict) else None
                entry["outputs"] = [o.get("item") for o in outs if isinstance(o, dict)]
            elif "result" in data:
                result = data["result"]
                entry["output"] = result.get("item") if isinstance(result, dict) else result
            if "ingredients" in data:
                ids = []
                for ing in data["ingredients"]:
                    if isinstance(ing, dict):
                        ids.append(ing.get("item") or ing.get("tag") or "?")
                    elif isinstance(ing, list):
                        ids.extend(i.get("item") for i in ing if isinstance(i, dict))
                entry["ingredients"] = ids
            out.setdefault(kind, []).append(entry)
    return out


def main():
    recipes = read_recipes()
    records = [json.loads(line) for line in (KNOWLEDGE / "scenes.jsonl").read_text(encoding="utf-8").splitlines()]
    for record in records:
        kind = SCENE_RECIPE_TYPES.get(record["id"])
        if not kind or kind not in recipes:
            record["recipes"] = None
            continue
        entries = recipes[kind]
        record["recipes"] = {
            "type": kind,
            "count": len(entries),
            "sample": [
                {"output": e.get("output"), "ingredients": e.get("ingredients")}
                for e in entries[:5]
            ],
        }
    (KNOWLEDGE / "recipes.json").write_text(
        json.dumps(recipes, ensure_ascii=False), encoding="utf-8")
    with (KNOWLEDGE / "scenes.jsonl").open("w", encoding="utf-8") as fh:
        for record in records:
            fh.write(json.dumps(record, ensure_ascii=False) + "\n")
    linked = sum(1 for r in records if r.get("recipes"))
    print(json.dumps({
        "scenes": len(records),
        "recipe_types": {k: len(v) for k, v in recipes.items()},
        "linked_scenes": linked,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
