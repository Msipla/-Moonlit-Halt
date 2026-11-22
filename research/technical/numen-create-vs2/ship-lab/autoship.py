"""Autonomous ship loop: natural language -> model plan -> build -> assemble -> drive -> verify.

Reuses the local ship-lab: VS 2.4.11 server, graphical client with shiptest.jar.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = r"D:\NumenLab\ship-lab"
SERVER = os.path.join(ROOT, "server")
CLIENT = os.path.join(ROOT, "client")
PY = r"E:\python\python.exe"
JAVA = r"D:\Android\openjdk\jdk-17.0.12\bin\java.exe"
RCON = r"D:\NumenLab\tools\rcon.py"
BUILDER = r"D:\NumenLab\tools\builder.py"
KEY = r"D:\NumenLab\client\config\numen\server\keys\deepseek.key"

PALETTE = {
    "minecraft:oak_planks", "minecraft:oak_fence", "vs_eureka:oak_ship_helm",
    "vs_eureka:anchor", "vs_eureka:balloon", "vs_eureka:white_balloon", "vs_eureka:floater",
}

PROMPT = """You design a small Eureka/Valkyrien Skies airship for Minecraft 1.20.1.
Answer with JSON only, no prose:
{"blocks":[{"p":[x,y,z],"id":"<block id>","facing":"north|south|east|west"}],"helm":[x,y,z],"reason":"<one sentence>"}
Rules:
- x and z are integers in -3..3; y is 0..5 (0 is the deck level, above the ground).
- Allowed ids: oak_planks, oak_fence, oak_ship_helm, anchor, white_balloon, floater (with the minecraft:/vs_eureka: prefixes).
- Exactly one oak_ship_helm, which must face a horizontal direction.
- The hull must be connected through shared faces; balloons must touch the hull or a pillar.
- At most 40 blocks. Use 4-6 balloons for lift and one anchor for stopping.
Goal: {goal}
"""


def call_model(goal):
    key = open(KEY, encoding="utf-8").read().strip()
    body = json.dumps({
        "model": "deepseek-chat",
        "messages": [
            {"role": "system", "content": "You are a compact Minecraft airship designer. Reply with JSON only."},
            {"role": "user", "content": PROMPT.replace("{goal}", goal)},
        ],
        "temperature": 0.2,
        "max_tokens": 1200,
    }).encode()
    request = urllib.request.Request(
        "https://api.deepseek.com/chat/completions", data=body,
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=180) as response:
        payload = json.loads(response.read())
    return payload["choices"][0]["message"]["content"]


def parse_plan(text):
    match = re.search(r"\{.*\}", text, re.S)
    if not match:
        raise ValueError("no json in model reply")
    plan = json.loads(match.group(0))
    with open(os.path.join(ROOT, "auto-reply.txt"), "w", encoding="utf-8") as handle:
        handle.write(text)
    for block in plan.get("blocks", []):
        name = block["id"].split(":")[-1]
        block["id"] = ("vs_eureka:" if name in {
            "oak_ship_helm", "anchor", "balloon", "white_balloon", "floater"} else "minecraft:") + name
    if "helm" not in plan:
        for block in plan["blocks"]:
            if "helm" in block["id"]:
                plan["helm"] = block["p"]
                break
    blocks = plan["blocks"]
    if len(blocks) > 40:
        raise ValueError("more than 40 blocks")
    ids = [b["id"] for b in blocks]
    if ids.count("vs_eureka:oak_ship_helm") != 1:
        raise ValueError("need exactly one helm")
    for block in blocks:
        if block["id"] not in PALETTE:
            raise ValueError("block outside palette: " + block["id"])
        x, y, z = block["p"]
        if not (-3 <= x <= 3 and 0 <= y <= 5 and -3 <= z <= 3):
            raise ValueError("block outside bounds: " + str(block["p"]))
    cells = {tuple(b["p"]) for b in blocks}
    start = tuple(blocks[0]["p"])
    seen, stack = {start}, [start]
    while stack:
        x, y, z = stack.pop()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            nxt = (x + dx, y + dy, z + dz)
            if nxt in cells and nxt not in seen:
                seen.add(nxt)
                stack.append(nxt)
    if len(seen) != len(cells):
        raise ValueError("hull is not connected")
    return plan


def build_plan(plan, base_y=-56):
    out = []
    for block in plan["blocks"]:
        x, y, z = block["p"]
        entry = {"pos": [x + 1, base_y + y, z + 1], "id": block["id"]}
        if block.get("facing"):
            entry["props"] = {"facing": block["facing"]}
        out.append(entry)
    return {"blocks": out}


def rcon(command):
    subprocess.run([PY, RCON, "25578", command], capture_output=True, timeout=60)


def start_server():
    process = subprocess.Popen(
        [JAVA, "-Dfile.encoding=UTF-8", "-Xms512m", "-Xmx2G", "-jar", "server-launch.jar", "nogui"],
        cwd=SERVER, stdout=open(os.path.join(SERVER, "auto-server.log"), "w", encoding="utf-8"),
        stderr=subprocess.DEVNULL)
    for _ in range(120):
        time.sleep(1)
        try:
            with open(os.path.join(SERVER, "auto-server.log"), encoding="utf-8", errors="ignore") as handle:
                if "Done (" in handle.read():
                    return process
        except FileNotFoundError:
            pass
    raise RuntimeError("server did not start")


def build_world(plan_json):
    process = start_server()
    try:
        rcon("forceload add -16 -16 32 32")
        rcon("fill -10 -60 -10 12 -48 12 minecraft:air")
        rcon("fill -10 -61 -10 12 -61 12 minecraft:grass_block")
        path = os.path.join(ROOT, "auto-plan.json")
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(plan_json, handle)
        result = subprocess.run([PY, BUILDER, "25578", path], capture_output=True, text=True, timeout=120)
        print(result.stdout.strip())
        rcon("save-all flush")
        time.sleep(1)
    finally:
        rcon("stop")
        process.wait(timeout=120)


def run_client(helm):
    target = os.path.join(CLIENT, "saves", "shiptest")
    shutil.rmtree(target, ignore_errors=True)
    shutil.copytree(os.path.join(SERVER, "world"), target)
    try:
        os.remove(os.path.join(target, "session.lock"))
    except FileNotFoundError:
        pass
    subprocess.run([PY, "-c",
                    "import nbtlib,sys; p=sys.argv[1]; f=nbtlib.load(p); d=f['Data']; "
                    "d['LevelName']=nbtlib.String('shiptest'); d['GameType']=nbtlib.Int(1); "
                    "d['allowCommands']=nbtlib.Byte(1); f.save()",
                    os.path.join(target, "level.dat")], check=True)
    with open(os.path.join(CLIENT, "shiptest.request"), "w", encoding="ascii") as handle:
        handle.write("%d %d %d" % tuple(helm))
    for name in ("shiptest-result.json", "shiptest-error.txt"):
        path = os.path.join(CLIENT, name)
        if os.path.exists(path):
            os.remove(path)
    process = subprocess.Popen(
        [JAVA, "@shiptest.args"], cwd=CLIENT,
        stdout=open(os.path.join(CLIENT, "auto-client.log"), "w", encoding="utf-8"),
        stderr=subprocess.DEVNULL)
    result_path = os.path.join(CLIENT, "shiptest-result.json")
    for _ in range(150):
        time.sleep(1)
        if os.path.exists(result_path):
            time.sleep(2)
            break
    process.terminate()
    try:
        process.wait(timeout=30)
    except subprocess.TimeoutExpired:
        process.kill()
    if not os.path.exists(result_path):
        error = os.path.join(CLIENT, "shiptest-error.txt")
        raise RuntimeError(open(error, encoding="utf-8").read() if os.path.exists(error) else "client produced no result")
    return json.load(open(result_path, encoding="utf-8"))


def summarize(plan, helm, result):
    samples = result.get("samples", [])
    phases = {}
    for sample in samples:
        phases.setdefault(sample["phase"], []).append(sample)
    metrics = {}
    for name in ("forward", "turn", "stop"):
        rows = phases.get(name)
        if rows:
            start, end = rows[0]["p"], rows[-1]["p"]
            metrics[name] = round(((end[0] - start[0]) ** 2 + (end[2] - start[2]) ** 2) ** 0.5, 3)
    return {
        "helm": helm,
        "mounted": result.get("mounted_through_native_helm"),
        "ship_id": result.get("ship_id"),
        "displacement": metrics,
        "blocks": len(plan["blocks"]),
        "reason": plan.get("reason"),
        "error": result.get("error"),
    }


def main():
    goal = " ".join(sys.argv[1:]) or "A small controllable airship that can move north, turn east and stop with an anchor."
    print("goal:", goal)
    for attempt in range(1, 4):
        reply = call_model(goal)
        try:
            plan = parse_plan(reply)
        except ValueError as error:
            print("attempt", attempt, "rejected:", error)
            goal = goal + "\nThe previous attempt was rejected: " + str(error) + ". Fix that and reply with JSON only."
            continue
        helm_local = plan["helm"]
        helm = (helm_local[0] + 1, -56 + helm_local[1], helm_local[2] + 1)
        print("attempt", attempt, "blocks", len(plan["blocks"]), "helm", helm)
        build_world(build_plan(plan))
        result = run_client(helm)
        summary = summarize(plan, helm, result)
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        if summary["mounted"] and sum(summary["displacement"].values()) > 1.0:
            print("ACCEPTED")
            with open(os.path.join(ROOT, "auto-result.json"), "w", encoding="utf-8") as handle:
                json.dump({"plan": plan, "summary": summary}, handle, ensure_ascii=False, indent=2)
            return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
