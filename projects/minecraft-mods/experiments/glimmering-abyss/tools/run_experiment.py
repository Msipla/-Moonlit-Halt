"""Build and test the release JAR in a fresh, localhost-only Fabric server.

Default: native server measurements and second-world determinism check.
--capture additionally launches the real Minecraft development client.
Minecraft/software dependencies stay in ignored runtime directories, not Git.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def release_jar():
    version = next(line.split('=',1)[1].strip() for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    return ROOT / f'build/libs/glimmering-abyss-{version}.jar'



def wait_for(check, timeout, message, process=None):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if check():
            return
        if process is not None and process.poll() is not None:
            raise RuntimeError(f"{message}: process exited {process.returncode}")
        time.sleep(0.25)
    raise TimeoutError(message)


def command(directory, text):
    target = directory / "abyss-command.txt"
    wait_for(lambda: not target.exists(), 60, "Previous test command did not finish")
    tmp = directory / "abyss-command.tmp"
    tmp.write_text(text + "\n", encoding="utf-8")
    tmp.replace(target)


def run(args):
    if not args.accept_eula:
        raise SystemExit("Pass --accept-eula after accepting https://aka.ms/MinecraftEULA for this isolated test server.")
    output = (ROOT / args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    gradle = [str(wrapper), "--no-daemon"]
    if not args.skip_build:
        subprocess.run(gradle + ["build", "stageTestMods"], cwd=ROOT, check=True)
    elif not release_jar().exists() or not (ROOT / "build/test-mods").exists():
        raise RuntimeError("--skip-build requires existing verified build artifacts")

    java = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")) if os.environ.get("JAVA_HOME") else "java"
    runtime = ROOT / ".runtime"
    runtime.mkdir(exist_ok=True)
    server_dir = runtime / ("server-" + str(time.time_ns()))
    server_dir.mkdir()
    if args.server_template:
        template = Path(args.server_template).resolve()
        if not (template / "fabric-server-launch.jar").exists() or not (template / "server.jar").exists():
            raise RuntimeError("Template must be a previously installed official Fabric 1.20.1 server runtime")
        for name in ["libraries", "versions", "server.jar", "fabric-server-launch.jar", "fabric-server-launcher.properties"]:
            source = template / name
            if source.is_dir():shutil.copytree(source, server_dir / name)
            elif source.exists():shutil.copy2(source, server_dir / name)
        (output / "installer.log").write_text("Reused official server bootstrap only; no mods, world or .fabric cache copied.\n", encoding="utf-8")
    else:
        installer = runtime / "fabric-installer-1.0.3.jar"
        url = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar"
        if not installer.exists():
            with urllib.request.urlopen(url, timeout=60) as response:installer.write_bytes(response.read())
        with urllib.request.urlopen(url + ".sha1", timeout=30) as response:expected = response.read().decode().split()[0]
        if hashlib.sha1(installer.read_bytes()).hexdigest() != expected:raise RuntimeError("Fabric installer checksum mismatch")
        with (output / "installer.log").open("w", encoding="utf-8") as log:
            subprocess.run([java, "-jar", str(installer), "server", "-mcversion", "1.20.1", "-loader", "0.14.25",
                            "-downloadMinecraft", "-dir", str(server_dir)], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    mods = server_dir / "mods"
    mods.mkdir(exist_ok=True)
    jar = release_jar()
    api = next((ROOT / "build/test-mods").glob("fabric-api-*.jar"))
    shutil.copy2(jar, mods / jar.name)
    shutil.copy2(api, mods / api.name)
    (server_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    base_properties = {
        "server-ip": "127.0.0.1", "server-port": "25585", "online-mode": "false",
        "level-seed": "74102002", "level-type": "minecraft:flat",
        "generator-settings": json.dumps({"biome": "minecraft:plains", "layers": [
            {"block": "minecraft:bedrock", "height": 1}, {"block": "minecraft:dirt", "height": 2},
            {"block": "minecraft:grass_block", "height": 1}], "structures": {}}),
        "difficulty": "peaceful", "gamemode": "creative", "spawn-protection": "0",
        "view-distance": "16" if (args.phase2 or args.phase3) else "12", "simulation-distance": "6", "max-players": "2",
        "max-tick-time": "120000", "enable-command-block": "false"
    }
    server_command = [java, "-Xms512M", "-Xmx1500M", "-XX:ActiveProcessorCount=4",
                      "-Dabyss.testServer=true", "-Djava.net.preferIPv4Stack=true",
                      "-jar", "fabric-server-launch.jar", "nogui"]

    def start(name, log_name):
        properties = dict(base_properties, **{"level-name": name})
        (server_dir / "server.properties").write_text("\n".join(f"{k}={v}" for k, v in properties.items()) + "\n", encoding="utf-8")
        log_path = output / log_name
        log = log_path.open("w", encoding="utf-8")
        process = subprocess.Popen(server_command, cwd=server_dir, stdout=log, stderr=subprocess.STDOUT)
        try:
            wait_for(lambda: log_path.exists() and "Done (" in log_path.read_text(encoding="utf-8", errors="replace"),
                     240, "Minecraft server did not become ready", process)
        except Exception:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=30)
            log.close()
            raise
        print("Real Fabric server ready:", name, flush=True)
        return process, log

    def stop(process, log):
        if process.poll() is None:
            command(server_dir, "stop")
            try:
                process.wait(timeout=90)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=30)
        log.close()

    results = server_dir / "abyss-results"
    process, log = start("study-first", "server-first.log")
    try:
        if args.capture:
            # The optional client harness is only enabled through this property.
            capture_started = time.time()
            with (output / "client.log").open("w", encoding="utf-8") as client_log:
                client_command = gradle + ["runClient", "-PabyssPhase3Capture" if args.phase3 else ("-PabyssPhase2Capture" if args.phase2 else "-PabyssCapture")]
                if args.skip_asset_download: client_command += ['-x','downloadAssets']
                subprocess.run(client_command, cwd=ROOT,
                               stdout=client_log, stderr=subprocess.STDOUT, check=True, timeout=900)
            expected_marker = "ABYSS_PHASE3_COMPLETE 19 walk_collision=true" if args.phase3 else "ABYSS_PHASE2_COMPLETE 17 walk_collision=true" if args.phase2 else "ABYSS_CAPTURE_COMPLETE 9 walk_collision=true"
            marker_present = expected_marker in (output / "client.log").read_text(encoding="utf-8", errors="replace")
            captures = ROOT / ("run-client/abyss-phase3-captures" if args.phase3 else ("run-client/abyss-phase2-captures" if args.phase2 else "run-client/abyss-captures"))
            manifest = json.loads((captures / "captures.json").read_text())
            if len(manifest) != (19 if args.phase3 else (17 if args.phase2 else 9)):
                raise RuntimeError("Real game capture did not produce the complete current manifest")
            # A redirected completion message can be absent even after Minecraft
            # wrote its final walk result. Require fresh files from this launch,
            # rather than counting an old manifest or relying on stdout alone.
            required = [captures / "captures.json", captures / "walk-collision.json"]
            required += [captures / row["file"] for row in manifest]
            if args.phase3: required += [captures / f"blue-platform-stability-{i}.png" for i in [1, 2]]
            if args.phase2: required += [captures / f"b-platform-stability-{i}.png" for i in [1, 2]]
            if not all(path.exists() and path.stat().st_mtime >= capture_started for path in required):
                raise RuntimeError("Current capture files are missing or stale")
            walk = json.loads((captures / "walk-collision.json").read_text())
            if not walk["walk_and_collision_pass"]:
                raise RuntimeError("Actual client walk/railing collision check failed")
            (output / "client-completion-check.json").write_text(json.dumps({
                "capture_started_epoch": capture_started, "stdout_marker_present": marker_present,
                "fresh_file_count": len(required), "fresh_file_check_pass": True,
                "positive_actual_walk_result": True,
                "proof": "Current-launch manifest, every screenshot, and final actual-client walk JSON."
            }, indent=2) + "\n")
            target = output / "screenshots"
            target.mkdir(exist_ok=True)
            for capture in manifest:
                shutil.copy2(captures / capture["file"], target / capture["file"])
            shutil.copy2(captures / "captures.json", target / "captures.json")
            shutil.copy2(captures / "walk-collision.json", output / "walk-collision.json")
            if args.phase3:
                for path in captures.glob('*-stability-*.png'):shutil.copy2(path,target/path.name)
                shutil.copy2(captures/'frame-intervals.json',output/'frame-intervals.json')
                for profile in ['blue','copper']:shutil.copy2(results/f'audit-glimmer-{profile}.json',output/f'audit-glimmer-{profile}.json')
                for profile in ['blue','copper']:shutil.copy2(results/f'audit-baseline-no-sources-{profile}.json',output/f'audit-baseline-no-sources-{profile}.json')
            if args.phase2:
                for path in captures.glob('*-stability-*.png'):shutil.copy2(path,target/path.name)
                for profile in ['a','b','c']:shutil.copy2(results/f'audit-glimmer-{profile}.json',output/f'audit-glimmer-{profile}.json')
                for profile in ['b','c']:shutil.copy2(results/f'audit-baseline-no-sources-{profile}.json',output/f'audit-baseline-no-sources-{profile}.json')
        if not args.capture or args.phase2 or args.phase3:
            for mode in ["baseline", "glimmer", "reference"]:
                command(server_dir, "abyss light " + mode)
                wait_for(lambda: not (server_dir / "abyss-command.txt").exists(), 60, "Light switch was not processed", process)
                time.sleep(5)
                command(server_dir, "abyss audit")
                wait_for(lambda: (results / f"audit-{mode}.json").exists(), 180, "Native audit did not finish", process)
        for mode in ["baseline", "glimmer", "reference"]:
            path = results / f"audit-{mode}.json"
            wait_for(path.exists, 180, "Native audit missing: " + mode, process)
            data = json.loads(path.read_text())
            if not data["native_light_profile_pass"] or not data["route_and_rail_pass"]:
                raise RuntimeError("Native game checks failed: " + mode)
            shutil.copy2(path, output / path.name)
            print("Native light / route checks passed:", mode, flush=True)
    finally:
        stop(process, log)

    if args.phase3:
        from phase3_validate import validate
        measured=validate(output)
        measured.update(minecraft='1.20.1',loader='Fabric 0.14.25',fabric_api='0.85.0+1.20.1',
            release_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
            native_server_light_checks=True,real_client_screenshots=21,
            real_client_walk_collision_pass=True,gradle_asset_download_skipped=args.skip_asset_download)
        (output/'summary.json').write_text(json.dumps(measured,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps(measured,ensure_ascii=False,indent=2),flush=True)
        return

    # Fresh world rather than a reload of already generated chunks.
    # New launcher directory as well as a new world: no cached game remap or chunks.
    first_runtime = server_dir
    server_dir = runtime / ("server-repeat-" + str(time.time_ns()))
    server_dir.mkdir()
    for name in ["libraries", "mods", "versions", "server.jar", "fabric-server-launch.jar",
                 "fabric-server-launcher.properties", "eula.txt"]:
        source = first_runtime / name
        if source.is_dir():
            shutil.copytree(source, server_dir / name)
        elif source.exists():
            shutil.copy2(source, server_dir / name)
    results = server_dir / "abyss-results"
    process, log = start("study-repeat", "server-repeat.log")
    try:
        command(server_dir, "abyss light glimmer")
        wait_for(lambda: not (server_dir / "abyss-command.txt").exists(), 90, "Repeat light switch did not finish", process)
        time.sleep(5)
        command(server_dir, "abyss audit")
        repeat = results / "audit-glimmer.json"
        wait_for(repeat.exists, 180, "Fresh-world audit did not finish", process)
        shutil.copy2(repeat, output / "audit-fresh-world.json")
        first = json.loads((output / "audit-glimmer.json").read_text())
        second = json.loads(repeat.read_text())
        match = first["generated_terrain_sha256"] == second["generated_terrain_sha256"]
        if not match or not second["native_light_profile_pass"] or not second["route_and_rail_pass"]:
            raise RuntimeError("Fresh-world determinism/native checks failed")
        summary = {"status": "PASS", "minecraft": "1.20.1", "loader": "Fabric 0.14.25",
                   "fabric_api": "0.85.0+1.20.1", "release_jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
                   "native_server_light_checks": True, "fresh_world_fingerprint_match": match,
                   "terrain_fingerprint": first["generated_terrain_sha256"],
                   "sampled_world_blocks": first["generated_world_sample_count"],
                   "real_client_screenshots": (19 if args.phase2 else 9) if args.capture else 0,
                   "real_client_walk_collision_pass": True if args.capture else None,
                   "performance_limit": "Cloud CPU/software OpenGL; not target desktop hardware or Android."}
        summary['gradle_asset_download_skipped']=args.skip_asset_download
        if args.phase2:
            previous=json.loads((ROOT/'evidence/phase1/summary.json').read_text())
            summary['comparison_screenshots']=12
            summary['matches_phase1_terrain_fingerprint']=first['generated_terrain_sha256']==previous['terrain_fingerprint']
            if not summary['matches_phase1_terrain_fingerprint']:raise RuntimeError('Phase 2 terrain differs from Phase 1 sampling')
            from phase2_validate import validate
            summary.update(validate(output))
        (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(summary, indent=2), flush=True)
    finally:
        stop(process, log)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-eula", action="store_true")
    parser.add_argument("--capture", action="store_true", help="Launch a real client; needs a working desktop/X display")
    parser.add_argument("--skip-build", action="store_true", help="Use already built JAR/API artifacts after a successful build")
    parser.add_argument("--server-template", help="Reuse a previously installed official Fabric server bootstrap; fresh worlds, mods and caches are still created")
    parser.add_argument("--output", default="evidence/latest")
    parser.add_argument('--phase3',action='store_true',help='Spatial terrain shader / alternate color real-client capture')
    parser.add_argument('--phase2',action='store_true',help='A/B/C environment captures; requires --capture')
    parser.add_argument('--skip-asset-download',action='store_true',help='Advanced: use already prepared graphics/font assets; audio is not validated')
    args=parser.parse_args()
    if args.phase2 and args.phase3:parser.error("Choose one capture protocol")
    if (args.phase2 or args.phase3) and not args.capture:parser.error('--phase2 requires --capture')
    run(args)
