"""Map timestamped MIDI onsets to the vanilla instruments in a Midicraft song."""

from collections import Counter, defaultdict

# Each base is the MIDI pitch of note=0 in Minecraft Java 1.20.1.
BASES = {
    "harp": 54, "bass": 30, "guitar": 42, "flute": 66, "bell": 78,
    "chime": 78, "xylophone": 78, "iron_xylophone": 54, "cow_bell": 66,
    "didgeridoo": 30, "bit": 54, "banjo": 54, "pling": 54,
    "basedrum": 54, "snare": 54, "hat": 54,
}
DRUMS = {
    35: "basedrum", 36: "basedrum", 37: "snare", 38: "snare",
    39: "snare", 40: "snare", 41: "basedrum", 42: "hat", 43: "basedrum",
    44: "hat", 45: "basedrum", 46: "hat", 47: "basedrum", 48: "basedrum",
    49: "hat", 50: "basedrum", 51: "hat", 52: "hat", 53: "hat",
    54: "hat", 55: "hat", 56: "cow_bell", 57: "hat", 59: "hat",
}


def mapping_for(program):
    family = program // 8
    if family == 0:
        return ("harp", "pling", "flute", "bass")
    if family == 1:
        return ("bell", "chime", "iron_xylophone", "harp")
    if family == 2:
        return ("pling", "harp", "flute", "guitar")
    if family == 3:
        return ("guitar", "banjo", "harp", "flute")
    if family == 4:
        return ("bass", "guitar", "harp")
    if family in (5, 6):
        return ("harp", "flute", "guitar")
    if family in (7, 8, 9):
        return ("flute", "harp", "guitar")
    if family in (10, 11, 12):
        return ("bit", "pling", "flute", "guitar")
    if family == 13:
        return ("banjo", "guitar", "harp", "flute")
    if family == 14:
        return ("xylophone", "iron_xylophone", "guitar")
    return ("harp", "flute", "guitar")


def map_pitch(pitch, ordered):
    # Prefer retaining the original register. Use an octave shift only if
    # none of the candidate vanilla timbres has that MIDI pitch.
    for distance in range(11):
        candidates = (pitch,) if distance == 0 else (pitch - distance * 12, pitch + distance * 12)
        for timbre in ordered:
            base = BASES[timbre]
            for sounding in candidates:
                if base <= sounding <= base + 24:
                    return timbre, sounding - base, sounding - pitch
    raise ValueError(f"Unreachable pitch: {pitch}")


def arrange(midi):
    events = []
    tracks = []
    for index, track in enumerate(midi.instruments):
        played = Counter()
        shifts = Counter()
        fallback_drums = Counter()
        candidates = mapping_for(track.program)
        for source in track.notes:
            if track.is_drum:
                sound = DRUMS.get(source.pitch, "hat")
                if source.pitch not in DRUMS:
                    fallback_drums[int(source.pitch)] += 1
                note, shift = 12, 0
            else:
                sound, note, shift = map_pitch(source.pitch, candidates)

            played[sound] += 1
            shifts[shift] += 1
            events.append({
                "track": index,
                "instrument": sound,
                "note": note,
                "tick": round(source.start * 20),
                "startSeconds": round(source.start, 4),
                "durationSeconds": round(source.end - source.start, 4),
                "velocity": int(source.velocity),
                "midiPitch": int(source.pitch),
            })
        tracks.append({
            "name": track.name,
            "program": int(track.program),
            "percussion": bool(track.is_drum),
            "vanillaInstruments": dict(played),
            "octaveChangesInSemitones": dict(shifts),
            "unmappedPercussionToHat": dict(fallback_drums),
        })
    if not events:
        raise ValueError("No playable events in the transcribed MIDI.")
    events.sort(key=lambda event: event["tick"])
    if len(events) > 2000 or events[-1]["tick"] > 2400:
        raise ValueError("The prototype accepts at most 2,000 note onsets and 120 seconds.")
    peak = max(Counter(event["tick"] for event in events).values())
    if peak > 30:
        raise ValueError(f"One 50 ms grid cell holds {peak} notes. Midicraft can place up to 30.")
    return events, tracks


def project(title, events):
    layers = defaultdict(list)
    for identifier, event in enumerate(events, start=1):
        layers[event["track"], event["instrument"]].append({
            "id": identifier,
            "midiNote": event["note"] + 54,
            "startTick": event["tick"] * 48,
            "durationTicks": max(1, round(event["durationSeconds"] * 960)),
            "velocity": event["velocity"],
        })
    return {
        "name": title,
        "ppq": 480,
        "tempoMicrosPerQuarter": 500_000,
        "layers": [
            {"name": f"{index + 1} / {instrument}", "instrument": instrument.upper(),
             "buildEnabled": True, "visible": True, "notes": notes}
            for (index, instrument), notes in layers.items()
        ],
        "nextNoteId": len(events) + 1,
        "endTick": max(note["startTick"] + note["durationTicks"]
                       for notes in layers.values() for note in notes),
        "speedQuarters": 4,
        "dedupeIdenticalNotes": False,
    }
