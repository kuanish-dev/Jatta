"""Collects reading texts for М11 from open sources and keeps only passages a B1 learner can read.

A passage is kept when at least 95% of its words are known and it brings at most 3 unknown words.
"Known" means: a form of one of the first ASSUMED_KNOWN_RANK words of NGSL 1.2 (a conservative
guess at B1 vocabulary), a word of the course, or a proper noun (names count as known in coverage
studies). Every other word is stored in "rare"; the app treats a rare word as known once the learner
holds it, so texts unlock as the course goes on.

Sources: Simple English Wikipedia (CC BY-SA 4.0) and public-domain books from Project Gutenberg.
Run from the repository root:  python3 -I scripts/collect_texts.py
"""

import csv
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "content/en/texts.json"
ASSUMED_KNOWN_RANK = 2000
MIN_COVERAGE = 0.95
MAX_UNKNOWN = 3
MIN_WORDS, MAX_WORDS = 50, 170
MAX_PER_WORK = 6
USER_AGENT = "JattaTextCollector/1.0 (https://github.com/kuanish-dev/Jatta)"

WIKI_TITLES = [
    "Job", "Employment", "Office", "Meeting", "Email", "Computer", "Computer program", "Software",
    "Programmer", "Internet", "Website", "Smartphone", "Company", "Business", "Money", "Bank",
    "Manager", "Team", "Teacher", "School", "University", "Book", "Library", "Coffee", "Tea",
    "Breakfast", "Sleep", "Memory", "Learning", "Language", "English language", "Kazakhstan",
    "Almaty", "Weather", "Holiday", "Bus", "Train", "Airport", "Doctor", "Hospital", "Sport",
    "Football", "Music", "Family", "Friendship", "Time", "Week", "Telephone", "Television",
    "Newspaper", "Shop", "City", "House", "Food", "Bread", "Water", "Car", "Bicycle", "Dog", "Cat",
]

BOOKS = [
    (11, "Alice's Adventures in Wonderland", "Lewis Carroll", 1865),
    (21, "Three Hundred Aesop's Fables", "Aesop, tr. George Fyler Townsend", 1867),
    (55, "The Wonderful Wizard of Oz", "L. Frank Baum", 1900),
    (14838, "The Tale of Peter Rabbit", "Beatrix Potter", 1902),
]

# The app's tokenizer (engine TextCoverage): lower-case letters with an optional inner apostrophe.
TOKEN = re.compile(r"[a-z]+(?:'[a-z]+)?")
CONTRACTION_STEMS = {"ca": "can", "wo": "will", "sha": "shall"}


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8")


def ngsl_known_forms():
    ranks = {}
    with open(ROOT / "sources/ngsl/NGSL_12_stats.csv", newline="") as f:
        for row in csv.DictReader(f):
            ranks.setdefault(row["Lemma"].lower(), int(row["SFI Rank"]))
    forms = set()
    for line in open(ROOT / "sources/ngsl/NGSL_12_lemmatized_for_teaching.csv"):
        if line.startswith("#") or not line.strip():
            continue
        words = [w.strip().lower() for w in line.split(",") if w.strip()]
        if ranks.get(words[0], 10**9) <= ASSUMED_KNOWN_RANK:
            forms.update(words)
    # NGSL keeps numbers, days and months in a supplementary list; a B1 learner knows them all.
    for line in open(ROOT / "sources/ngsl/NGSL_SUP_lemmatized.csv", encoding="utf-8-sig"):
        if not line.startswith("#"):
            forms.update(w.strip().lower() for w in line.split(",") if w.strip())
    return forms


def course_forms():
    forms = set()
    for path in sorted((ROOT / "content/en/topics").glob("*.json")):
        for item in json.loads(path.read_text())["items"]:
            if item["kind"] == "rule":
                continue
            for text in [item["en"], *item.get("also", []), *[e["gap"] for e in item["examples"] if e.get("gap")]]:
                forms.update(TOKEN.findall(text.lower()))
    return forms


def is_known(token, known):
    if token in known:
        return True
    if token.endswith("n't"):
        stem = token[:-3]
        return CONTRACTION_STEMS.get(stem, stem) in known
    if "'" in token:
        return token.split("'")[0] in known
    return False


def proper_nouns(text):
    """Words written with a capital letter somewhere other than the start of a sentence."""
    names = set()
    for match in re.finditer(r"[A-Za-z]+(?:'[A-Za-z]+)?", text):
        word = match.group()
        before = text[:match.start()].rstrip(" \"'“‘(")
        sentence_start = before == "" or before[-1] in ".!?:\n"
        if word[0].isupper() and not sentence_start and word.lower() != "i":
            names.add(word.lower())
            names.add(word.lower().split("'")[0])
    return names


def judge(text, known):
    tokens = TOKEN.findall(text.lower())
    names = proper_nouns(text)
    rare = sorted({t for t in tokens if not is_known(t, known) and t not in names and t.split("'")[0] not in names})
    return tokens, rare


def clean(text):
    text = text.replace("’", "'").replace("‘", "'").replace("“", '"').replace("”", '"').replace("—", " - ").replace("–", "-")
    text = re.sub(r"\[\d+\]", "", text)
    text = re.sub(r"\[Illustration[^\]]*\]", "", text)
    text = re.sub(r"_([^_]+)_", r"\1", text)
    return re.sub(r"\s+", " ", text).strip()


def is_prose(paragraph):
    """Running sentences: not a heading, a list or a table of contents."""
    if "CHAPTER" in paragraph or paragraph.isupper():
        return False
    return paragraph.endswith((".", "!", "?", '."', '!"', '?"', ".'", "!'", "?'"))


def passages(paragraphs):
    """Prose paragraphs of a good length; short neighbours are joined, long ones skipped."""
    buffer = []
    for paragraph in paragraphs:
        paragraph = clean(paragraph)
        if not paragraph or len(paragraph.split()) < 4:
            continue
        if not is_prose(paragraph):
            buffer = []
            continue
        buffer.append(paragraph)
        words = len(" ".join(buffer).split())
        if words >= MIN_WORDS:
            if words <= MAX_WORDS:
                yield " ".join(buffer)
            buffer = []


def readable(text, known, course):
    if not text.isascii():
        return None
    tokens, rare = judge(text, known | course)
    if not tokens:
        return None
    unknown_count = sum(1 for t in tokens if t in rare)
    if 1 - unknown_count / len(tokens) < MIN_COVERAGE or len(rare) > MAX_UNKNOWN:
        return None
    # Store every word outside the assumed vocabulary, course words included: the app decides
    # per learner which of them are already known.
    _, outside_base = judge(text, known)
    return outside_base


def wiki_texts(known, course):
    seen = set()  # several titles redirect to the same page
    for title in WIKI_TITLES:
        query = urllib.parse.urlencode({
            "action": "query", "format": "json", "prop": "extracts|info", "explaintext": 1,
            "exsectionformat": "plain", "inprop": "url", "redirects": 1, "titles": title,
        })
        page = next(iter(json.loads(fetch("https://simple.wikipedia.org/w/api.php?" + query))["query"]["pages"].values()))
        time.sleep(0.5)
        extract, revision = page.get("extract", ""), page.get("lastrevid")
        if not extract or not revision or page["title"] in seen:
            continue
        seen.add(page["title"])
        kept = 0
        for text in passages(extract.split("\n")):
            rare = readable(text, known, course)
            if rare is None:
                continue
            kept += 1
            yield {
                "id": f"sw-{re.sub(r'[^a-z0-9]+', '-', page['title'].lower()).strip('-')}-{kept}",
                "title": page["title"],
                "text": text,
                "rare": rare,
                "source": {
                    "name": "Simple English Wikipedia",
                    "work": page["title"],
                    "author": "Simple English Wikipedia contributors",
                    "url": f"https://simple.wikipedia.org/w/index.php?oldid={revision}",
                    "license": "CC BY-SA 4.0",
                    "changes": "Отрывок, разметка и ссылки на сноски убраны",
                },
            }
            if kept == MAX_PER_WORK:
                break


def book_texts(known, course):
    for number, title, author, year in BOOKS:
        raw = fetch(f"https://www.gutenberg.org/cache/epub/{number}/pg{number}.txt").replace("\r\n", "\n")
        body = raw.split("*** START OF", 1)[1].split("\n", 1)[1].split("*** END OF", 1)[0]
        kept = 0
        for text in passages(re.split(r"\n\s*\n", body.replace("\n\n\n", "\n\n"))):
            rare = readable(text.replace("\n", " "), known, course)
            if rare is None:
                continue
            kept += 1
            yield {
                "id": f"pg{number}-{kept}",
                "title": title,
                "text": text,
                "rare": rare,
                "source": {
                    "name": "Project Gutenberg",
                    "work": f"{title} ({year})",
                    "author": author,
                    "url": f"https://www.gutenberg.org/ebooks/{number}",
                    "license": "Public domain",
                    "changes": "Отрывок",
                },
            }
            if kept == MAX_PER_WORK:
                break
        time.sleep(1)


def main():
    known, course = ngsl_known_forms(), course_forms()
    texts = list(wiki_texts(known, course)) + list(book_texts(known, course))
    OUT.write_text(json.dumps({
        "assumedKnown": f"NGSL 1.2 ranks 1-{ASSUMED_KNOWN_RANK}",
        "texts": texts,
    }, ensure_ascii=False, indent=2) + "\n")
    print(f"{len(texts)} texts -> {OUT.relative_to(ROOT)}")
    for name in ("Simple English Wikipedia", "Project Gutenberg"):
        print(f"  {name}: {sum(1 for t in texts if t['source']['name'] == name)}")


if __name__ == "__main__":
    main()
