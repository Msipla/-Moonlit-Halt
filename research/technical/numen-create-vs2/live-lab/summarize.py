"""Summarize a completed run from independent snapshots and metered tool traces."""
import argparse
import collections
import datetime as dt
import json
import re
from pathlib import Path


def lines(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line]


def state(path):
    return json.loads(path.read_text())


def written(content):
    return [
        {"completed": int(done), "target": int(wanted), "placed": int(placed),
         "direct": int(direct or 0), "cleared": int(cleared)}
        for done, wanted, placed, direct, cleared in re.findall(
            r"built (\d+)/(\d+) block\(s\); placed (\d+)(?: \((\d+) written straight in[^)]+\))?[^;\n]*?, cleared (\d+)",
            content,
        )
    ]


def boat_summary(current, baseline):
    changes = {k: b for k, b in current.items() if k not in baseline or b != baseline[k]}
    scaffolds = sorted(tuple(map(int,k.split(","))) for k,b in changes.items() if b["Name"]=="minecraft:cobblestone")
    modified = {tuple(map(int, k.split(","))): b for k, b in changes.items()
                if b["Name"] not in {"minecraft:cobblestone","minecraft:dirt"}}
    if not modified:
        return {"newStructureBlocks": 0, "cobblestoneResidue": scaffolds}
    unvisited = set(modified)
    components = []
    while unvisited:
        start = unvisited.pop()
        component = {start: modified[start]}
        queue = collections.deque([start])
        while queue:
            pos = queue.popleft()
            for axis in range(3):
                for direction in (-1, 1):
                    neighbor = tuple(v + direction if i == axis else v for i, v in enumerate(pos))
                    if neighbor in unvisited:
                        unvisited.remove(neighbor)
                        component[neighbor] = modified[neighbor]
                        queue.append(neighbor)
        components.append(component)
    def score(component):
        names = {block["Name"] for block in component.values()}
        span_x = max(p[0] for p in component) - min(p[0] for p in component) + 1
        span_z = max(p[2] for p in component) - min(p[2] for p in component) + 1
        return (span_x == 7 and span_z == 13, "minecraft:barrel" in names, len(component))
    structure = max(components, key=score)
    minima = [min(pos[i] for pos in structure) for i in range(3)]
    maxima = [max(pos[i] for pos in structure) for i in range(3)]
    blocks = {tuple(map(int, k.split(","))): b for k, b in current.items()}
    non_solid = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}

    def clear(p):
        return blocks.get(p, {}).get("Name", "minecraft:air") in non_solid

    def passable(x, y, z):
        return clear((x, y, z)) and clear((x, y + 1, z)) and not clear((x, y - 1, z))

    entrances = []
    [xmin, ymin, zmin], [xmax, ymax, zmax] = minima, maxima
    for y in range(ymin + 1, ymax):
        for x in range(xmin + 1, xmax):
            if passable(x, y, zmax) and passable(x, y, zmax - 1):
                entrances.append((x, y, zmax))
    walks = []
    for entry in entrances:
        visited = {entry}
        queue = collections.deque([entry])
        while queue:
            x, y, z = queue.popleft()
            for p in [(x + 1, y, z), (x - 1, y, z), (x, y, z - 1), (x, y, z + 1)]:
                if xmin < p[0] < xmax and zmin <= p[2] <= zmax and p not in visited and passable(*p):
                    queue.append(p)
                    visited.add(p)
        walks.append({"entry": entry, "walkable": sorted(visited), "northmostZ": min(p[2] for p in visited),
                      "roofAbove": sum(any(not clear((x, yy, z)) for yy in range(y + 2, ymax + 1))
                                       for x, y, z in visited)})
    return {
        "newStructureBlocks": len(structure), "bounds": [minima, maxima],
        "otherChangedBlocks": len(modified) - len(structure),
        "cobblestoneResidue": scaffolds,
        "componentSizes": sorted((len(c) for c in components),reverse=True),
        "dimensionsXYZ": [b - a + 1 for a, b in zip(minima, maxima)],
        "interiorRoofGaps": [(x, ymax, z) for x in range(xmin + 1, xmax)
                             for z in range(zmin + 1, zmax) if clear((x, ymax, z))],
        "windowBlocks": [{"pos": p, "block": b} for p, b in sorted(structure.items()) if "glass" in b["Name"]],
        "woolMarkers": [{"pos": p, "block": b} for p, b in sorted(structure.items()) if b["Name"].endswith("_wool")],
        "storage": [{"pos": p, "block": b} for p, b in sorted(structure.items()) if b["Name"] == "minecraft:barrel"],
        "walks": walks,
    }


def summarize(run):
    request_trace = lines(run / "model.jsonl")
    events = lines(run / "events.jsonl")
    label = run.name.split("-", 1)[0]
    metering = collections.Counter()
    builds = []
    tool_replies, calls = set(), collections.Counter()
    loaded_skill = False
    probe_programs = 0
    cost = 0
    for row in request_trace:
        metering.update({k: v for k, v in row.get("usage", {}).items() if isinstance(v, int)})
        usage = row.get("usage", {})
        start = dt.datetime.fromisoformat(row["utc"].replace("Z", "+00:00"))
        peak = start.weekday() < 5 and (1 <= start.hour < 4 or 6 <= start.hour < 10)
        rates = (.022, .66, 1.98) if row["model"] == "deepseek-v4-pro" else (.003, .15, .6)
        amounts = [usage.get(k, 0) for k in ("prompt_cache_hit_tokens", "prompt_cache_miss_tokens", "completion_tokens")]
        cost += (2 if peak else 1) * sum(rate * amount for rate, amount in zip(rates, amounts)) / 1_000_000
        calls.update(call["name"] for call in row["calls"])
        for call in row["calls"]:
            if call["name"] == "lua":
                try:
                    code = json.loads(call["arguments"]).get("code", "")
                except (ValueError, AttributeError):
                    continue
                if re.search(r"createlab\.machine\.inspect\b", code):
                    probe_programs += 1
        for item in row["context"]:
            if item["role"] == "tool" and item["tool_call_id"] not in tool_replies:
                tool_replies.add(item["tool_call_id"])
                receipt = item["content"] or ""
                loaded_skill |= '<skill_content name="create_kinetics">' in receipt
                builds.extend(written(receipt))
    snapshot_events = [event for event in events if event["type"] == "snapshot"]
    server_log = (run / "minecraft.log").read_text()
    walking = re.findall(r"\[numen-path\].*?结局 .*?走了 (\d+) 步 挖 (\d+) 放 (\d+) 开关门", server_log)
    walk_counts = [sum(int(entry[i]) for entry in walking) for i in range(3)]
    command_setblock = re.findall(r"\[LabBot: Changed the block at (-?\d+, -?\d+, -?\d+)\]", server_log)
    command_fill = [int(n) for n in re.findall(r"\[LabBot: (?:Successfully filled|Changed) (\d+) block(?:s|\(s\))?\]", server_log)]
    dispatch_errors = re.findall(r"\[numen-dispatch[^\]]*\] result id=\S+ (\S+) → (failed|error)", server_log)
    result = {
        "run": run.name, "requests": len(request_trace), "calls": calls,
        "models": sorted({r["model"] for r in request_trace}),
        "httpStatus": collections.Counter(str(r.get("status", "unavailable")) for r in request_trace),
        "receivedCreateSkill": loaded_skill, "luaProgramsReferencingProbe": probe_programs,
        "reportedUsage": {key: val for key,val in metering.items() if isinstance(val,int)},
        "estimatedUsd": round(cost, 8), "successfulBuildReceipts": builds,
        "serverDispatchErrors": len(dispatch_errors),
        "agentCommandWriteReceipts": {"setblockChanges":len(command_setblock), "fillChanges":sum(command_fill)},
        "walkingFromPathReceipts": dict(zip(("steps","dug","placed"),walk_counts)),
        "results": [e for e in events if e["type"] in ("create_result","feed_result","feed_skipped","agent_timeout","agent_return","scope_result","skill_baseline","error")],
    }
    request_times=[dt.datetime.fromisoformat(e["utc"]) for e in events if e["type"]=="chat"]
    if request_times and snapshot_events and dt.datetime.fromisoformat(snapshot_events[-1]["utc"]) >= request_times[0]:
        result["secondsFromFirstChatToLastSnapshot"] = round((dt.datetime.fromisoformat(snapshot_events[-1]["utc"])-request_times[0]).total_seconds(),3)
    if snapshot_events and (run / "baseline.json").exists():
        baseline = state(run / "baseline.json")["blocks"]
        current = baseline
        result["snapshots"] = []
        for event in snapshot_events[1:]:
            data = state(run / (event["label"] + ".json"))
            current = data["blocks"]
            changes = [(k, baseline.get(k), current.get(k))
                       for k in sorted(baseline.keys() | current.keys()) if baseline.get(k) != current.get(k)]
            outside = [[k, before, after] for k, before, after in changes
                       if any(p < a or p > b for p, a, b in zip(map(int, k.split(",")), [-20,-61,-20], [20,-40,20]))]
            basic = {key: event[key] for key in ("label","create","control","sand")}
            basic.update(changedBlocks=len(changes), outside=outside,
                         addedKinds=collections.Counter(after["Name"] for _, before, after in changes if after and not before),
                         removedKinds=collections.Counter(before["Name"] for _, before, after in changes if before and not after),
                         replacedKinds=collections.Counter(f'{before["Name"]} -> {after["Name"]}'
                                                           for _,before,after in changes if before and after))
            result["snapshots"].append(basic)
        if label == "boat":
            full_baseline = run / "original-flat.json"
            boat_baseline = state(full_baseline)["blocks"] if full_baseline.exists() else baseline
            result["boat"] = boat_summary(current, boat_baseline)
        fault_path = run / "fault-injected.json"
        after_path = run / "after-agent-repair.json"
        if fault_path.exists() and after_path.exists():
            broken = state(fault_path)["blocks"]
            after = state(after_path)["blocks"]
            result["changesAfterFault"] = [
                {"cell": key, "before": broken.get(key), "after": after.get(key)}
                for key in sorted(broken.keys() | after.keys()) if broken.get(key) != after.get(key)
            ]
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("runs", type=Path, nargs="+")
    args = parser.parse_args()
    print(json.dumps([summarize(run) for run in args.runs], ensure_ascii=False, indent=2))
