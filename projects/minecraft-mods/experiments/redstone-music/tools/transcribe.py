"""Call external transcription tools. Outputs are reviewable before block generation."""

import argparse
import os
from pathlib import Path
import subprocess
import sys

import numpy as np
import pretty_midi
import soundfile as sf


def run_python(module, *args):
    subprocess.run([sys.executable, "-m", module, *map(str, args)], check=True)


def transcribe(audio, work, device):
    work.mkdir(parents=True, exist_ok=True)
    pcm = work / "source.wav"
    subprocess.run([
        "ffmpeg", "-v", "error", "-y", "-i", str(audio), "-vn",
        "-t", "121", "-ar", "44100", "-ac", "2", str(pcm),
    ], check=True)
    if sf.info(pcm).duration > 120:
        raise ValueError("Trim the input to at most 120 seconds.")
    run_python("demucs", "-n", "htdemucs_6s", "-d", device, "--shifts", "0",
               "--out", work, pcm)
    stems = work / "htdemucs_6s" / pcm.stem

    harmonic, rate = sf.read(stems / "other.wav")
    for part in ("guitar", "piano", "vocals"):
        signal, signal_rate = sf.read(stems / f"{part}.wav")
        if signal_rate != rate or signal.shape != harmonic.shape:
            raise ValueError(f"Unexpected Demucs stem length or rate: {part}")
        harmonic += signal
    harmonic_file = work / "harmonic.wav"
    sf.write(harmonic_file, np.clip(harmonic, -1.0, 1.0), rate)

    print("Transcribing the separated harmonic mix using YourMT3+.", flush=True)
    harmonic_midi = work / "harmonic.mid"
    run_python("mt3_infer.cli", "transcribe", harmonic_file, "-o", harmonic_midi,
               "-m", "yourmt3", "--device", device)
    combined = pretty_midi.PrettyMIDI(str(harmonic_midi))
    # The separated bass and drum models supply these roles once.
    combined.instruments = [part for part in combined.instruments
                            if not part.is_drum and part.program // 8 != 4]
    if not combined.instruments:
        raise ValueError("YourMT3+ returned no melody or harmonic accompaniment.")
    print("Transcribing the bass stem using Basic Pitch.", flush=True)
    from basic_pitch import ICASSP_2022_MODEL_PATH
    from basic_pitch.inference import predict

    _, bass, _ = predict(stems / "bass.wav", ICASSP_2022_MODEL_PATH,
                         onset_threshold=.50, frame_threshold=.30, minimum_note_length=120,
                         minimum_frequency=35, maximum_frequency=180)
    for part in bass.instruments:
        part.program = 33
        part.name = "Bass stem (assigned GM 33)"
    bass.write(str(work / "bass.mid"))
    combined.instruments.extend(bass.instruments)

    print("Transcribing the drum stem using ADTOF.", flush=True)
    from adtof_pytorch import transcribe_to_midi

    drums_midi = work / "drums.mid"
    transcribe_to_midi(stems / "drums.wav", drums_midi, device=device)
    drums = pretty_midi.PrettyMIDI(str(drums_midi))
    for part in drums.instruments:
        part.name = "Drums (ADTOF)"
    combined.instruments.extend(drums.instruments)
    target = work / "review.mid"
    combined.write(str(target))
    for track in combined.instruments:
        print(f"{track.name}: {len(track.notes)} onsets, GM {track.program}, drums={track.is_drum}")
    print(target)


if __name__ == "__main__":
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("audio", type=Path)
    cli.add_argument("work", type=Path)
    cli.add_argument("--device", choices=("cpu", "cuda"), default="cpu")
    args = cli.parse_args()
    # ONNX defaults to spawning a physically sized pool, which can slow down
    # other tools on smaller machines. Users can increase this externally.
    os.environ.setdefault("OMP_NUM_THREADS", "4")
    transcribe(args.audio.resolve(), args.work.resolve(), args.device)
