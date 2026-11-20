"""Generate static warnings locally. Requires eSpeak NG 1.52.0; never runs in the Android app."""
from pathlib import Path
import argparse, array, hashlib, json, math, os, subprocess, wave

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--espeak", type=Path, required=True)
    args = parser.parse_args()
    executable = args.espeak.resolve()
    texts = json.loads((ROOT / "tools/warning_scripts.json").read_text(encoding="utf-8"))
    output = ROOT / "app/src/main/res/raw"
    output.mkdir(parents=True, exist_ok=True)
    report = {"generator": "eSpeak NG 1.52.0 (build time only)", "voice_variant": "f3", "words_per_minute": 140, "clips": {}}
    work = ROOT / ".tools/audio-input"
    work.mkdir(parents=True, exist_ok=True)
    for language, text in texts.items():
        source = work / (language + ".txt")
        source.write_text(text, encoding="utf-8")
        target = output / ("warning_" + language + ".wav")
        voice = ("en-gb" if language == "en" else language) + "+f3"
        subprocess.run([str(executable), "--path=" + str(executable.parent), "-v", voice,
                        "-s", "140", "-a", "145", "-p", "45", "-w", str(target), "-f", str(source)],
                        check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        with wave.open(str(target), "rb") as stream:
            assert stream.getnchannels() == 1 and stream.getsampwidth() == 2
            duration = stream.getnframes() / stream.getframerate()
            samples = array.array("h", stream.readframes(stream.getnframes()))
            rms = math.sqrt(sum(value * value for value in samples) / len(samples)) / 32768
            peak = max(abs(value) for value in samples) / 32768
            assert 3 <= duration <= 30 and rms > 0.01 and peak <= 1
        report["clips"][language] = {"seconds": duration, "rms": rms, "peak": peak,
            "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "script": text}
    destination = ROOT / "docs/validation/audio.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(report, ensure_ascii=True, indent=2))

if __name__ == "__main__":
    main()
