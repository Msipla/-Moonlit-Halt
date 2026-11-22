"""Read-only query API over the extracted Create Ponder knowledge.

Plain functions, structured dicts, no HTTP, no extra dependencies. Every result
carries its source and keeps simulated scene values clearly marked.

    import pondex
    pondex.stats()
    pondex.search("cogwheel", limit=3)
    pondex.machine("mechanical_mixer")
    pondex.steps("millstone", limit=10)
    pondex.recipes(recipe_type="mixing", limit=5)
    pondex.recipes(item="create:andesite_alloy")
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
KNOWLEDGE = ROOT / "knowledge"
SOURCE = {
    "mod": "create-fabric-6.0.8.1+build.1744-mc1.20.1.jar",
    "scenes": "assets/create/ponder/**/*.nbt + decompiled ponder scenes",
    "recipes": "data/create/recipes/<type>/*.json",
}
MAX_TEXT = 160


def _load_scenes():
    path = KNOWLEDGE / "scenes.jsonl"
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


def _load_recipes():
    path = KNOWLEDGE / "recipes.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


_SCENES = None
_RECIPES = None
_BY_ID = None


def _scenes():
    global _SCENES, _BY_ID
    if _SCENES is None:
        _SCENES = _load_scenes()
        _BY_ID = {}
        for row in _SCENES:
            _BY_ID.setdefault(row["id"], row)
    return _SCENES


def _recipes_index():
    global _RECIPES
    if _RECIPES is None:
        _RECIPES = _load_recipes()
    return _RECIPES


def stats():
    """Coverage and provenance. Use this before claiming completeness."""
    rows = _scenes()
    recipes = _recipes_index()
    return {
        "scenes": len(rows),
        "with_scene_nbt": sum(1 for r in rows if r.get("nbt")),
        "with_teaching_text": sum(1 for r in rows if r.get("text_count")),
        "with_simulated_values": sum(1 for r in rows if r.get("simulated_count")),
        "with_recipe_link": sum(1 for r in rows if r.get("recipes")),
        "ordered_steps": sum(len(r["steps"]) for r in rows),
        "recipe_types": {k: len(v) for k, v in recipes.items()},
        "source": SOURCE,
        "caveat": (
            "110 scene scripts were extracted from 30 decompiled classes; the jar holds 178 scene .nbt files. "
            "109 scripts have candidate asset paths, spanning 102 distinct assets. Heuristic matches are not authoritative."
        ),
    }


def machines():
    """Machines that have an explicit mapping to a real recipe type."""
    out = []
    for row in _scenes():
        if not row.get("recipes"):
            continue
        out.append({
            "id": row["id"],
            "title": row.get("title"),
            "scene": row.get("nbt"),
            "recipe_type": row["recipes"]["type"],
            "recipe_count": row["recipes"]["count"],
        })
    return sorted(out, key=lambda m: m["id"])


def search(keyword, limit=5):
    """Keyword search over titles and teaching text. Returns compact hits only."""
    needle = (keyword or "").lower().strip()
    if not needle:
        return {"query": keyword, "hits": [], "note": "empty keyword"}
    scored = []
    for row in _scenes():
        score = 0
        if needle in (row.get("id") or "").lower():
            score += 5
        if needle in (row.get("title") or "").lower():
            score += 3
        snippet = None
        for step in row["steps"]:
            text = step.get("text") or ""
            if needle in text.lower():
                score += 1
                snippet = snippet or text
        if score:
            scored.append((score, row, snippet))
    scored.sort(key=lambda item: (-item[0], item[1]["id"]))
    hits = [{
        "id": row["id"],
        "title": row.get("title"),
        "scene": row.get("nbt"),
        "snippet": (snippet or "")[:MAX_TEXT] or None,
    } for _, row, snippet in scored[:limit]]
    # fall back to recipe data when the phrase is an item name, not scene text
    recipe_hits = []
    normalized = needle.replace(" ", "_")
    if len(hits) < limit and len(normalized) >= 4:
        index = _recipes_index()
        produced, used = [], []
        for kind, entries in index.items():
            for entry in entries:
                output = (entry.get("output") or "").lower()
                item = {"type": kind, "output": entry.get("output"),
                        "ingredients": entry.get("ingredients")}
                if normalized in output:
                    produced.append(item)
                elif any(normalized in (i or "").lower() for i in entry.get("ingredients") or []):
                    used.append(item)
        recipe_hits = (produced + used)[:limit]
    return {
        "query": keyword,
        "matched": len(scored),
        "returned": len(hits),
        "truncated": len(scored) > len(hits),
        "hits": hits,
        "recipe_hits": recipe_hits,
        "recipe_note": "recipe hits match item ids, not scene narration" if recipe_hits else None,
        "source": SOURCE,
    }


def machine(scene_id):
    """Full record for one scene: ordered steps, simulated values, recipes."""
    _scenes()
    row = _BY_ID.get(scene_id)
    if not row:
        candidates = [r["id"] for r in _scenes() if scene_id.lower() in r["id"].lower()][:5]
        return {"error": "not found", "id": scene_id, "did_you_mean": candidates}
    steps = []
    ticks = 0
    for step in row["steps"]:
        kind = step["kind"]
        if kind == "idle":
            ticks += step["ticks"]
            continue
        if kind == "text":
            steps.append({"t": ticks, "kind": "text", "text": (step.get("text") or "")[:MAX_TEXT]})
        elif kind == "simulated_speed":
            steps.append({"t": ticks, "kind": "simulated_speed", "rpm": step["rpm"],
                          "note": "demo value injected by the scene, not a game constraint"})
        else:
            steps.append({"t": ticks, "kind": "action", "call": step.get("call")})
    return {
        "id": row["id"],
        "title": row.get("title"),
        "class": f'{row["class"]}.{row["method"]}',
        "scene_nbt": row.get("nbt"),
        "step_count": len(steps),
        "steps": steps[:60],
        "truncated": len(steps) > 60,
        "recipes": row.get("recipes"),
        "source": SOURCE,
    }


def steps(scene_id, limit=20, texts_only=True):
    """Teaching steps (text) with their scene-time offset, capped for context budget."""
    record = machine(scene_id)
    if "error" in record:
        return record
    rows = [s for s in record["steps"] if (s["kind"] == "text" if texts_only else True)]
    return {
        "id": record["id"],
        "title": record["title"],
        "returned": min(len(rows), limit),
        "total": len(rows),
        "steps": rows[:limit],
        "source": SOURCE["scenes"],
    }


def recipes(recipe_type=None, item=None, limit=10):
    """Query real recipes by processing type and/or item id (output or ingredient)."""
    index = _recipes_index()
    if recipe_type:
        entries = index.get(recipe_type, [])
        if not entries:
            return {"error": "unknown recipe type", "type": recipe_type,
                    "known": sorted(index.keys())}
    else:
        entries = [entry for group in index.values() for entry in group]
    if item:
        needle = item.lower()
        entries = [e for e in entries
                   if needle in (e.get("output") or "").lower()
                   or any(needle in (i or "").lower() for i in (e.get("ingredients") or []))]
    sample = [{"output": e.get("output"), "ingredients": e.get("ingredients")}
              for e in entries[:limit]]
    return {
        "type": recipe_type,
        "item": item,
        "matched": len(entries),
        "returned": len(sample),
        "truncated": len(entries) > len(sample),
        "recipes": sample,
        "source": SOURCE["recipes"],
    }


def coverage_gap():
    """Explain the difference between extracted scenes and scene assets in the jar."""
    rows = _scenes()
    resolved = {r["nbt"] for r in rows if r.get("nbt")}
    jar_nbt = json.loads((ROOT / "nbt-index.json").read_text(encoding="utf-8-sig"))
    unresolved_scenes = [r["id"] for r in rows if not r.get("nbt")]
    unused_assets = sorted(set(jar_nbt.values()) - resolved)
    return {
        "scene_scripts": len(rows),
        "scene_assets_in_jar": len(set(jar_nbt.values())),
        "scripts_with_candidate_assets": sum(bool(r.get("nbt")) for r in rows),
        "distinct_candidate_assets": len(resolved),
        "scripts_without_nbt": unresolved_scenes,
        "assets_without_script": unused_assets[:25],
        "assets_without_script_total": len(unused_assets),
        "note": (
            "One script can use several scene assets (multi-part demos), and some assets belong to scenes "
            "registered outside the decompiled classes. Treat this as partial coverage."
        ),
    }


def _main(argv):
    if len(argv) < 2:
        print(__doc__)
        return
    action = argv[1]
    payload = None
    if action == "stats":
        payload = stats()
    elif action == "coverage":
        payload = coverage_gap()
    elif action == "machines":
        payload = machines()
    elif action == "search" and len(argv) > 2:
        payload = search(argv[2], limit=int(argv[3]) if len(argv) > 3 else 5)
    elif action == "machine" and len(argv) > 2:
        payload = machine(argv[2])
    elif action == "steps" and len(argv) > 2:
        payload = steps(argv[2], limit=int(argv[3]) if len(argv) > 3 else 20)
    elif action == "recipes" and len(argv) > 2:
        payload = recipes(recipe_type=argv[2], limit=int(argv[3]) if len(argv) > 3 else 10)
    elif action == "item" and len(argv) > 2:
        payload = recipes(item=argv[2], limit=int(argv[3]) if len(argv) > 3 else 10)
    else:
        print(__doc__)
        return
    print(json.dumps(payload, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    import sys
    _main(sys.argv)
