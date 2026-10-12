"""Optional build-host fix for Loom 1.2.8 on hosts denying AF_UNIX with EPERM.

Not part of the mod. Normal Windows/Linux desktops do not need this.
Only patches an explicitly supplied private Gradle cache, retains the original,
and changes CurrentPlatform's IOException branch to report unsupported sockets.
The source is obtained from Fabric's official Maven publication at runtime;
neither the third-party source nor the patched binary is distributed here.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import urllib.request
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle-user-home", type=Path, required=True)
    parser.add_argument("--gradle-install", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--restore", action="store_true")
    args = parser.parse_args()
    cache = args.gradle_user_home.resolve()
    if cache == (Path.home() / ".gradle").resolve():
        raise SystemExit("Use a private task cache; this helper refuses the shared default cache.")
    jar = next((cache / "caches/modules-2/files-2.1/net.fabricmc/fabric-loom/1.2.8").glob("*/*.jar"))
    backup = jar.with_suffix(".jar.original")
    if args.restore:
        jar.write_bytes(backup.read_bytes())
        print("Restored original Loom publication")
        return
    base = "https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.2.8/fabric-loom-1.2.8"
    with urllib.request.urlopen(base + ".jar.sha1", timeout=30) as response:
        expected = response.read().decode().split()[0]
    if backup.exists():
        original = backup.read_bytes()
    else:
        original = jar.read_bytes()
        if hashlib.sha1(original).hexdigest() != expected:
            raise RuntimeError("Original Loom checksum mismatch")
        backup.write_bytes(original)
    with urllib.request.urlopen(base + "-sources.jar", timeout=30) as response:
        sources = response.read()
    with zipfile.ZipFile(io.BytesIO(sources)) as archive:
        source = archive.read("net/fabricmc/loom/util/CurrentPlatform.java").decode()
    old = "throw new UncheckedIOException(e);"
    if source.count(old) != 1:
        raise RuntimeError("Unexpected Loom source; refusing to patch")
    source = source.replace(old, "return false; // Restricted build host: Unix sockets unavailable.")
    work = Path(__file__).resolve().parents[1] / ".runtime/loom-compat"
    work.mkdir(parents=True, exist_ok=True)
    java = work / "CurrentPlatform.java"
    java.write_text(source)
    dependencies = [jar] + list((args.gradle_install / "lib").rglob("*.jar"))
    classpath = ":".join(map(str, dependencies))
    subprocess.run([str(args.jdk / "bin/javac"), "-cp", classpath, "-d", str(work), str(java)], check=True)
    class_name = "net/fabricmc/loom/util/CurrentPlatform.class"
    patched = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(original)) as old_archive, zipfile.ZipFile(patched, "w", zipfile.ZIP_DEFLATED) as new_archive:
        for entry in old_archive.infolist():
            data = (work / class_name).read_bytes() if entry.filename == class_name else old_archive.read(entry.filename)
            new_archive.writestr(entry, data)
    jar.write_bytes(patched.getvalue())
    record = {"loom": "1.2.8", "scope": "private build-host cache only; no Minecraft classes changed",
              "original_sha256": hashlib.sha256(original).hexdigest(),
              "patched_sha256": hashlib.sha256(patched.getvalue()).hexdigest(),
              "change": "CurrentPlatform reports AF_UNIX unavailable on IOException instead of aborting initialization"}
    (work / "record.json").write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps(record, indent=2))


if __name__ == "__main__":
    main()
