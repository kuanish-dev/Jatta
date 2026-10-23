"""Builds content/en/placement.json, the word list of the placement test.

The test is a yes/no vocabulary test with pseudowords (Meara & Buxton 1987; LexTALE uses the same
idea): the learner says which words they know, and saying "yes" to words that don't exist shows how
much to discount. Real words are sampled from NGSL 1.2 frequency bands, so the result reads as "how
many of the most frequent words you know". Each band mixes words of the course, which the result
then places directly, with random NGSL words, which keep the band estimate fair.

Run from the repository root:  python3 -I scripts/build_placement.py
"""

import csv
import json
import random
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "content/en/placement.json"
BANDS = [(1, 500), (501, 1000), (1001, 1500), (1501, 2000), (2001, 2500), (2501, 2809)]
WORDS_PER_BAND = 8
COURSE_WORDS_PER_BAND = 4
# Checked against NGSL and a 235k-word English dictionary (/usr/share/dict/words) by this script.
PSEUDOWORDS = [
    "plimp", "drust", "marbish", "slonder", "gorvent", "pellick",
    "vonter", "cradish", "fendle", "spratch", "bralt", "clember",
]


def main():
    ranks = {}
    with open(ROOT / "sources/ngsl/NGSL_12_stats.csv", newline="") as f:
        for row in csv.DictReader(f):
            ranks.setdefault(row["Lemma"].lower(), int(row["SFI Rank"]))

    dictionary = {w.strip().lower() for w in open("/usr/share/dict/words")}
    for word in PSEUDOWORDS:
        assert word not in dictionary and word not in ranks, f"{word} is a real word"

    course = {}
    for path in sorted((ROOT / "content/en/topics").glob("*.json")):
        for item in json.loads(path.read_text())["items"]:
            if item["kind"] != "rule" and item.get("rank"):
                course[item["en"]] = item["rank"]

    random.seed(42)
    bands = []
    for low, high in BANDS:
        in_band = lambda rank: low <= rank <= high
        chosen = sorted((w for w, r in course.items() if in_band(r)), key=course.get)[:COURSE_WORDS_PER_BAND]
        pool = sorted(w for w, r in ranks.items() if in_band(r) and re.fullmatch(r"[a-z]{4,}", w) and w not in course)
        chosen += random.sample(pool, WORDS_PER_BAND - len(chosen))
        bands.append({"from": low, "to": high, "words": sorted(chosen, key=lambda w: ranks[w])})

    OUT.write_text(json.dumps({
        "source": "NGSL 1.2 (CC BY-SA 4.0), bands of frequency rank",
        "bands": bands,
        "pseudowords": PSEUDOWORDS,
    }, ensure_ascii=False, indent=2) + "\n")
    print(f"{sum(len(b['words']) for b in bands)} words + {len(PSEUDOWORDS)} pseudowords -> {OUT.relative_to(ROOT)}")
    for band in bands:
        print(f"  {band['from']}-{band['to']}: {', '.join(band['words'])}")


if __name__ == "__main__":
    main()
