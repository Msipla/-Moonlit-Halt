import argparse
from pathlib import Path
import numpy as np
import pretty_midi
import soundfile as sf

parser = argparse.ArgumentParser()
parser.add_argument("output", type=Path)
parser.add_argument("soundfont", type=Path)
parser.add_argument("--lead", choices=("guitar", "flute"), default="guitar")
args = parser.parse_args()
args.output.mkdir(exist_ok=True, parents=True)
score = pretty_midi.PrettyMIDI(initial_tempo=100)
lead_part = pretty_midi.Instrument(27 if args.lead == "guitar" else 73, name=args.lead + " lead")
piano = pretty_midi.Instrument(0, name="Piano chords")
bass = pretty_midi.Instrument(33, name="Fingered bass")
drums = pretty_midi.Instrument(0, is_drum=True, name="Drums")
score.instruments.extend((lead_part, piano, bass, drums))
chords = [(60,64,67), (57,60,64), (57,60,65), (59,62,67)]
lead = [(72,76,79,76), (69,72,76,72), (69,72,77,76), (74,71,67,71)]
for bar in range(10):
    start = bar * 2.4
    chord = chords[bar % 4]
    melody = lead[bar % 4]
    root = (36, 33, 41, 43)[bar % 4]
    for beat in range(4):
        at = start + beat * .6
        lead_part.notes.append(pretty_midi.Note(90 if args.lead == "guitar" else 107, melody[beat], at, at + .37))
        bass.notes.append(pretty_midi.Note(90, root if beat != 2 else root + 7, at, at + .40))
        if beat % 2 == 0:
            piano.notes.extend(pretty_midi.Note(88 if args.lead == "guitar" else 72, p, at, at + .82) for p in chord)
        drums.notes.append(pretty_midi.Note(110, 36 if beat % 2 == 0 else 38, at, at + .10))
        drums.notes.append(pretty_midi.Note(80, 42, at + .30, at + .10 + .30))
score.write(str(args.output / "reference.mid"))
wave = score.fluidsynth(fs=44100, synthesizer=str(args.soundfont))
sf.write(args.output / "fixture.wav", wave[:24 * 44100], 44100)
