"""Hear the planned vanilla instruments before exporting the redstone circuits."""

import argparse
import json
from pathlib import Path

import numpy as np
import pretty_midi
import soundfile as sf

from arrange import arrange

SAMPLES = {
    "basedrum": "bd", "bass": "bassattack", "harp": "harp2",
    "chime": "icechime", "xylophone": "xylobone",
}
RATE = 44_100


def render(events, assets, index, output):
    catalog = json.loads((assets / "indexes" / f"{index}.json").read_text(encoding="utf-8"))["objects"]
    mix = np.zeros((events[-1]["tick"] * RATE // 20) + 4 * RATE, dtype=np.float32)
    cache = {}
    last = 0
    for event in events:
        instrument, note = event["instrument"], event["note"]
        key = instrument, note
        if key not in cache:
            name = SAMPLES.get(instrument, instrument)
            asset_key = f"minecraft/sounds/note/{name}.ogg"
            digest = catalog[asset_key]["hash"]
            file = assets / "objects" / digest[:2] / digest
            if not file.is_file():
                raise FileNotFoundError(f"Launch vanilla Minecraft 1.20.1 to populate the sound asset: {asset_key}")
            signal, sample_rate = sf.read(file, dtype="float32")
            if signal.ndim > 1:
                signal = signal.mean(axis=1)
            pitch = 2 ** ((note - 12) / 12)
            speed = pitch * sample_rate / RATE
            positions = np.arange(0, len(signal) - 1, speed)
            cache[key] = np.interp(positions, np.arange(len(signal)), signal).astype(np.float32)
        clip = cache[key]
        offset = event["tick"] * RATE // 20
        end = min(len(mix), offset + len(clip))
        # Vanilla note blocks use a fixed gain per onset. Normalize the mix as
        # a whole so the relative levels and simultaneous hits are audible.
        mix[offset:end] += clip[:end - offset]
        last = max(last, end)
    peak = np.max(np.abs(mix))
    sf.write(output, mix[:last] / max(1.0, peak / .94), RATE, subtype="PCM_16")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("midi", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--assets", type=Path, required=True, help="Official .minecraft/assets directory")
    parser.add_argument("--index", default="5", help="Java 1.20.1 asset index (default: 5)")
    args = parser.parse_args()
    events, _ = arrange(pretty_midi.PrettyMIDI(str(args.midi)))
    render(events, args.assets, args.index, args.output)
