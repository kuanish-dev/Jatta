"""Проверяет файлы тем так же, как ContentLoader и ContentTest, до запуска Gradle.

    python3 -I scripts/check_topic.py content/en/topics/money-words.json [...]
    python3 -I scripts/check_topic.py --ranks account save polite
"""
import csv
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOPICS = ROOT / "content/en/topics"
NGSL = ROOT / "sources/ngsl/NGSL_12_stats.csv"
ID = re.compile(r"^[a-z0-9-]+$")


def ngsl_ranks():
    ranks = {}
    with open(NGSL, newline="") as f:
        for row in csv.DictReader(f):
            ranks.setdefault(row["Lemma"].lower(), int(row["SFI Rank"]))
    return ranks


def normalize(text):
    return re.sub(r"[^a-z0-9' ]", "", text.lower()).strip()


def whole_word(sentence, gap):
    return re.search(r"\b" + re.escape(gap) + r"\b", sentence) is not None


def check(path, ranks, all_ids):
    problems = []
    try:
        topic = json.loads(Path(path).read_text(encoding="utf-8"))
    except ValueError as e:
        return [f"{path}: не JSON: {e}"]
    tid = topic.get("id")
    if tid != Path(path).stem:
        problems.append(f"id «{tid}» не совпадает с именем файла")
    if not ID.match(tid or ""):
        problems.append(f"id «{tid}»: только латиница, цифры, дефис")
    for key in ("title", "intro"):
        if not str(topic.get(key, "")).strip():
            problems.append(f"нет {key}")
    known = {"id", "title", "intro", "requires", "confusableWith", "items"}
    for key in topic:
        if key not in known:
            problems.append(f"лишнее поле темы: {key}")
    items = topic.get("items", [])
    words = [i for i in items if i.get("kind") != "rule"]
    if 1 <= len(words) <= 3:
        problems.append(f"в теме {len(words)} слова: для выбора из вариантов нужно хотя бы 4")
    for item in items:
        where = f"{tid}/{item.get('id')}"
        for key in item:
            if key not in {"id", "kind", "en", "ru", "also", "rank", "note", "examples", "audio"}:
                problems.append(f"{where}: лишнее поле {key}")
        iid = item.get("id", "")
        if not ID.match(iid):
            problems.append(f"{where}: id только из латиницы, цифр и дефиса")
        if iid in all_ids:
            problems.append(f"{where}: id уже занят в теме {all_ids[iid]}")
        all_ids[iid] = tid
        kind = item.get("kind")
        if kind not in ("word", "phrase", "rule"):
            problems.append(f"{where}: kind должен быть word, phrase или rule")
        for key in ("en", "ru"):
            if not str(item.get(key, "")).strip():
                problems.append(f"{where}: нет {key}")
        if kind == "word":
            if not iid.startswith("w-"):
                problems.append(f"{where}: id слова начинается с w-")
            expected = ranks.get(item.get("en", "").lower())
            if item.get("rank") != expected:
                problems.append(f"{where}: rank должен быть {expected} (NGSL 1.2), сейчас {item.get('rank')}")
        if kind == "phrase" and "rank" in item:
            problems.append(f"{where}: у сочетаний rank не ставится")
        if kind == "rule" and "rank" in item:
            problems.append(f"{where}: у правил rank не ставится")
        if kind != "rule" and not item.get("audio") and not str(item.get("note", "")).strip():
            problems.append(f"{where}: нет note")
        examples = item.get("examples", [])
        if not examples:
            problems.append(f"{where}: нет примеров")
        for ex in examples:
            for key in ex:
                if key not in {"en", "ru", "gap", "cue", "wrong", "also"}:
                    problems.append(f"{where}: лишнее поле примера {key}")
            en, gap = ex.get("en", ""), ex.get("gap")
            if not en.strip() or not str(ex.get("ru", "")).strip():
                problems.append(f"{where}: у примера нет en или ru")
            if gap is not None:
                if not whole_word(en, gap):
                    problems.append(f"{where}: gap «{gap}» не стоит целым словом в «{en}»")
                for w in ex.get("wrong", []):
                    if normalize(w) == normalize(gap):
                        problems.append(f"{where}: неверный вариант «{w}» равен ответу")
        if kind != "rule" and not any(ex.get("gap") for ex in examples):
            problems.append(f"{where}: нужен хотя бы один пример с gap")
        if kind == "rule":
            if not any(ex.get("gap") for ex in examples):
                problems.append(f"{where}: правилу нужны примеры с gap")
            if not any(ex.get("gap") and len(ex.get("wrong", [])) >= 2 for ex in examples):
                problems.append(f"{where}: правилу нужен пример с gap и хотя бы 2 wrong")
        if item.get("audio") and not any(ex.get("gap") for ex in examples):
            problems.append(f"{where}: записанной фразе нужен пример с gap")
    return problems


def main(args):
    ranks = ngsl_ranks()
    if args and args[0] == "--ranks":
        for word in args[1:]:
            print(word, ranks.get(word.lower(), "— нет в NGSL, rank не ставить"))
        return 0
    paths = [Path(a) for a in args] or sorted(TOPICS.glob("*.json"))
    all_ids = {}
    for other in sorted(TOPICS.glob("*.json")):
        if other.resolve() in {p.resolve() for p in paths}:
            continue
        for item in json.loads(other.read_text(encoding="utf-8")).get("items", []):
            all_ids[item.get("id")] = other.stem
    failed = 0
    for path in paths:
        problems = check(path, ranks, all_ids)
        if problems:
            failed += 1
            print(f"{path}:")
            for p in problems:
                print("  ", p)
        else:
            print(f"{path}: ок")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
