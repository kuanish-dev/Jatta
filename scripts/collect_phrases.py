import argparse
import bz2
import csv
import json
import re
import subprocess
import tarfile
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SELECTION = ROOT / "sources/tatoeba/phrases.json"
TOPICS = ROOT / "content/en/topics"
AUDIO = ROOT / "content/en/audio"
EXPORTS = "https://downloads.tatoeba.org/exports"
FREE_LICENSES = ("CC BY 4.0", "CC BY-SA 4.0")
USER_AGENT = "JattaPhraseCollector/1.0 (https://github.com/kuanish-dev/Jatta)"
WORD = re.compile(r"[A-Za-z]+")
STEMS = {"ca": "can", "wo": "will", "sha": "shall"}
TRIM = "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.15"
FFMPEG_FILTER = f"{TRIM},areverse,{TRIM},areverse,loudnorm=I=-18:TP=-2"
DOWNLOADS_AT_ONCE = 4

csv.field_size_limit(10**9)


def fetch(url, path):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        path.write_bytes(response.read())


def exports(directory):
    directory.mkdir(parents=True, exist_ok=True)
    files = {
        "eng_sentences.tsv": "per_language/eng/eng_sentences.tsv.bz2",
        "rus_sentences.tsv": "per_language/rus/rus_sentences.tsv.bz2",
        "eng-rus_links.tsv": "per_language/eng/eng-rus_links.tsv.bz2",
        "sentences_with_audio.csv": "sentences_with_audio.tar.bz2",
        "user_languages.csv": "user_languages.tar.bz2",
    }
    for name, remote in files.items():
        if (directory / name).exists():
            continue
        archive = directory / Path(remote).name
        print("downloading", remote)
        fetch(f"{EXPORTS}/{remote}", archive)
        if remote.endswith(".tar.bz2"):
            with tarfile.open(archive) as tar:
                tar.extract(name, directory, filter="data")
        else:
            (directory / name).write_bytes(bz2.decompress(archive.read_bytes()))
    return directory


def sentences(path):
    result = {}
    for line in open(path, encoding="utf-8"):
        sid, _, text = line.rstrip("\n").split("\t", 2)
        result[int(sid)] = text
    return result


def ngsl_ranks():
    ranks = {}
    for row in csv.DictReader(open(ROOT / "sources/ngsl/NGSL_12_stats.csv", newline="")):
        ranks.setdefault(row["Lemma"].lower(), int(row["SFI Rank"]))
    forms = {}
    for line in open(ROOT / "sources/ngsl/NGSL_12_lemmatized_for_teaching.csv"):
        if line.startswith("#") or not line.strip():
            continue
        words = [w.strip().lower() for w in line.split(",") if w.strip()]
        if words[0] in ranks:
            for word in words:
                forms.setdefault(word, ranks[words[0]])
    return forms


def gap_word(text, ranks):
    candidates = []
    for match in WORD.finditer(text):
        word = match.group()
        after = text[match.end():match.end() + 1]
        if len(word) < 3 or after == "'" or (word[0].isupper() and match.start() > 0):
            continue
        candidates.append((ranks.get(word.lower(), 5000), len(word), word))
    return max(candidates)[2]


def original(directory, sid, audio_id, only_voice):
    path = directory / "mp3" / f"{audio_id}.mp3"
    if not path.exists():
        path.parent.mkdir(exist_ok=True)
        url = f"https://audio.tatoeba.org/sentences/eng/{sid}.mp3" if only_voice else f"https://tatoeba.org/audio/download/{audio_id}"
        fetch(url, path)
    return path


def convert(source, path):
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-i", str(source), "-af", FFMPEG_FILTER,
         "-ac", "1", "-ar", "22050", "-c:a", "aac", "-b:a", "40k", "-movflags", "+faststart", str(path)],
        check=True,
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--exports", default=str(ROOT / "build/tatoeba"))
    parser.add_argument("--dry-run", action="store_true", help="print the phrases, write nothing")
    parser.add_argument("--text-only", action="store_true", help="write the topics, leave the recordings as they are")
    args = parser.parse_args()
    directory = exports(Path(args.exports))

    selection = json.loads(SELECTION.read_text())
    english = sentences(directory / "eng_sentences.tsv")
    russian = sentences(directory / "rus_sentences.tsv")
    translations = {}
    for line in open(directory / "eng-rus_links.tsv"):
        en, ru = map(int, line.split())
        translations.setdefault(en, []).append(ru)
    native = {
        row[2] for row in csv.reader(open(directory / "user_languages.csv", encoding="utf-8"), delimiter="\t")
        if len(row) >= 3 and row[0] == "eng" and row[1] == "5"
    }
    voices, recordings = {}, {}
    for row in csv.reader(open(directory / "sentences_with_audio.csv", encoding="utf-8"), delimiter="\t"):
        sid, audio_id, user, license_ = (row + [""] * 4)[:4]
        if not sid.isdigit():
            continue
        recordings[int(sid)] = recordings.get(int(sid), 0) + 1
        if license_ in FREE_LICENSES and user in native:
            voices.setdefault(int(sid), (int(audio_id), user, license_))
    ranks = ngsl_ranks()

    if not args.dry_run and not args.text_only:
        AUDIO.mkdir(parents=True, exist_ok=True)
        chosen = [sid for topic in selection["topics"] for sid in topic["sentences"]]
        with ThreadPoolExecutor(DOWNLOADS_AT_ONCE) as pool:
            sources = dict(zip(chosen, pool.map(lambda sid: original(directory, sid, voices[sid][0], recordings[sid] == 1), chosen)))
        for old in AUDIO.glob("*.m4a"):
            old.unlink()
        for sid in chosen:
            convert(sources[sid], AUDIO / f"ph-{sid}.m4a")
        print("recordings:", len(chosen))

    for topic in selection["topics"]:
        items = []
        for sid in topic["sentences"]:
            en = english[sid].replace("’", "'").replace("“", '"').replace("”", '"')
            ru = selection["translations"].get(str(sid)) or min((russian[r] for r in translations[sid] if r in russian), key=len)
            gap = selection["gaps"].get(str(sid)) or gap_word(en, ranks)
            _, user, license_ = voices[sid]
            if args.dry_run:
                print(f"{sid}|{en}|{ru}|{gap}")
                continue
            items.append({
                "id": f"ph-{sid}",
                "kind": "phrase",
                "en": en,
                "ru": ru,
                "examples": [{"en": en, "ru": ru, "gap": gap}],
                "audio": {
                    "file": f"ph-{sid}.m4a",
                    "by": user,
                    "license": license_,
                    "source": f"https://tatoeba.org/sentences/show/{sid}",
                },
            })
        if not args.dry_run:
            body = {"id": topic["id"], "title": topic["title"], "intro": selection["intro"], "items": items}
            (TOPICS / f"{topic['id']}.json").write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n")
            print(topic["id"], len(items))


if __name__ == "__main__":
    main()
