"""Собирает content/en/basic-words.json: формы первой тысячи слов NGSL 1.2, которые ученик B1 знает.

    python3 -I scripts/build_basic_words.py
"""
import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "content/en/basic-words.json"
KNOWN_RANK = 1000


def main():
    ranks = {}
    with open(ROOT / "sources/ngsl/NGSL_12_stats.csv", newline="") as f:
        for row in csv.DictReader(f):
            ranks.setdefault(row["Lemma"].lower(), int(row["SFI Rank"]))
    forms = set()
    for line in open(ROOT / "sources/ngsl/NGSL_12_lemmatized_for_teaching.csv"):
        if line.startswith("#") or not line.strip():
            continue
        words = [w.strip().lower() for w in line.split(",") if w.strip()]
        if ranks.get(words[0], 10**9) <= KNOWN_RANK:
            forms.update(words)
    for line in open(ROOT / "sources/ngsl/NGSL_SUP_lemmatized.csv", encoding="utf-8-sig"):
        if not line.startswith("#"):
            forms.update(w.strip().lower() for w in line.split(",") if w.strip())
    data = {
        "source": "NGSL 1.2 (Browne, Culligan, Phillips), CC BY-SA 4.0: lemmas ranked 1–1000 with their forms, plus NGSL supplementary words",
        "rank": KNOWN_RANK,
        "forms": sorted(forms),
    }
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=0) + "\n")
    print(len(forms), "forms ->", OUT)


if __name__ == "__main__":
    main()
