import argparse
import collections
import csv
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SELECTION = ROOT / "sources/tatoeba/sentences.json"
PHRASES = ROOT / "sources/tatoeba/phrases.json"
TOPICS = ROOT / "content/en/topics"
OUT = ROOT / "content/en/sentences.json"
KNOWN_RANK = 1000
PER_ITEM = 3
MAX_TRANSLATIONS = 6
WORDS = range(4, 11)
NAMES = {"tom", "mary"}
SUFFIXES = ("n't", "'s", "'re", "'m", "'ll", "'ve", "'d")
STEMS = {"ca": "can", "wo": "will", "sha": "shall"}
LEFT_OUT = {
    "kill", "killed", "killing", "kills", "killer", "murder", "murdered", "suicide", "rape", "sex", "sexy",
    "naked", "bomb", "war", "terrorist", "terrorists", "gun", "guns", "shoot", "shot", "drug", "drugs",
    "blood", "corpse", "dead", "die", "died", "dies", "hell", "damn", "stupid", "idiot", "god", "church",
}
TOKEN = re.compile(r"[a-z]+(?:'[a-z]+)?")


def sentences(path):
    result = {}
    for line in open(path, encoding="utf-8"):
        sid, _, text = line.rstrip("\n").split("\t", 2)
        result[int(sid)] = text
    return result


def ngsl_forms():
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
                forms[word] = min(forms.get(word, ranks[words[0]]), ranks[words[0]])
    return forms


def tokens(text):
    result = []
    for word in TOKEN.findall(text.lower()):
        suffix = next((s for s in SUFFIXES if word.endswith(s) and len(word) > len(s)), None)
        if suffix:
            stem = word[: -len(suffix)]
            result += [STEMS.get(stem, stem) if suffix == "n't" else stem, suffix]
        else:
            result.append(word)
    return result


def inflections(word):
    listed = [f for f, lemma in LEMMA_OF.items() if lemma == word]
    if listed:
        return set(listed)
    stem = word[:-1] if word.endswith("e") else word
    result = {word, word + "s", word + "es", word + "d", word + "ed", stem + "ing"}
    if word.endswith("y"):
        result |= {word[:-1] + "ies", word[:-1] + "ied"}
    return result


def course_items():
    items = {}
    for path in sorted(TOPICS.glob("*.json")):
        for item in json.loads(path.read_text())["items"]:
            if item["kind"] == "rule" or "audio" in item:
                continue
            variants = set()
            for head in [item["en"]] + item.get("also", []):
                first, *rest = tokens(head)
                variants |= {tuple([form] + rest) for form in inflections(first)}
            variants |= {tuple(tokens(e["gap"])) for e in item["examples"] if e.get("gap")}
            items[item["id"]] = variants
    return items


def lemma_of():
    result = {}
    for line in open(ROOT / "sources/ngsl/NGSL_12_lemmatized_for_teaching.csv"):
        if line.startswith("#") or not line.strip():
            continue
        words = [w.strip().lower() for w in line.split(",") if w.strip()]
        for word in words:
            result.setdefault(word, words[0])
    return result


LEMMA_OF = lemma_of()


def items_in(words, index):
    found, covered = set(), set()
    for i, word in enumerate(words):
        for item, form in index.get(word, ()):
            if tuple(words[i:i + len(form)]) == form:
                found.add(item)
                covered |= set(range(i, i + len(form)))
    return found, covered


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--exports", default=str(ROOT / "build/tatoeba"))
    args = parser.parse_args()
    directory = Path(args.exports)
    selection = json.loads(SELECTION.read_text())
    skip = set(selection["skip"])
    skip |= {sid for topic in json.loads(PHRASES.read_text())["topics"] for sid in topic["sentences"]}

    english = sentences(directory / "eng_sentences.tsv")
    russian = sentences(directory / "rus_sentences.tsv")
    to_russian, to_english = collections.defaultdict(list), collections.defaultdict(list)
    for line in open(directory / "eng-rus_links.tsv"):
        en, ru = map(int, line.split())
        if en in english and ru in russian:
            to_russian[en].append(ru)
            to_english[ru].append(en)

    forms = ngsl_forms()
    known = {form for form, rank in forms.items() if rank <= KNOWN_RANK} | NAMES | set(SUFFIXES)
    items = course_items()
    index = collections.defaultdict(list)
    for item, variants in items.items():
        for form in variants:
            index[form[0]].append((item, form))

    candidates = []
    for sid, translations in to_russian.items():
        text = english[sid]
        if sid in skip or re.search(r"\d", text):
            continue
        words = tokens(text)
        if len([w for w in words if w not in SUFFIXES]) not in WORDS or LEFT_OUT & set(words):
            continue
        if any(w[0].isupper() and w not in ("I", "Tom", "Mary") for w in re.findall(r"[A-Za-z]+", text)[1:]):
            continue
        found, covered = items_in(words, index)
        if not found or any(w not in known and i not in covered for i, w in enumerate(words)):
            continue
        clean = [r for r in translations if not re.search(r"[A-Za-z0-9]", russian[r])]
        if not clean:
            continue
        main = max(clean, key=lambda r: (len(to_english[r]), -len(russian[r]), -r))
        en = [text] + [english[e] for e in sorted(to_english[main]) if e != sid]
        ru = [russian[main]] + [russian[r] for r in sorted(clean) if r != main]
        candidates.append({"id": sid, "en": en[:MAX_TRANSLATIONS], "ru": ru[:MAX_TRANSLATIONS], "items": sorted(found)})

    candidates.sort(key=lambda s: (-min(len(s["en"]), 4) - min(len(s["ru"]), 4), len(s["items"]), len(s["en"][0]), s["id"]))
    chosen, per_item = [], collections.Counter()
    for sentence in candidates:
        if all(per_item[i] >= PER_ITEM for i in sentence["items"]):
            continue
        chosen.append(sentence)
        per_item.update(sentence["items"])
    chosen.sort(key=lambda s: s["id"])

    head = {"source": "Tatoeba", "license": "CC BY 2.0 FR", "url": "https://tatoeba.org/sentences/show/",
            "assumedKnown": f"NGSL 1.2 ranks 1-{KNOWN_RANK}"}
    lines = [json.dumps(s, ensure_ascii=False) for s in chosen]
    OUT.write_text(json.dumps(head, ensure_ascii=False)[:-1] + ',\n"sentences": [\n' + ",\n".join(lines) + "\n]}\n")
    missing = sorted(set(items) - set(per_item))
    print(f"{len(chosen)} sentences for {len(per_item)} of {len(items)} course items; none for: {', '.join(missing)}")


if __name__ == "__main__":
    main()
