from datetime import datetime, timedelta, timezone

from fsrs import Optimizer, Rating, ReviewLog
from fsrs.scheduler import DEFAULT_PARAMETERS

GRADES = {"A": (Rating.Again, "Again"), "H": (Rating.Hard, "Hard"), "G": (Rating.Good, "Good"), "E": (Rating.Easy, "Easy")}

HISTORIES = [
    [(0, "G"), (2, "G"), (7, "A"), (8, "G"), (12, "H"), (30, "E")],
    [(0, "A"), (1, "A"), (2, "G"), (5, "G"), (11, "G")],
    [(3, "E"), (20, "G"), (80, "A"), (81, "G"), (90, "G")],
    [(4, "G"), (4, "G"), (9, "G"), (9, "A"), (10, "H")],
    [(10, "G")],
    [(1, "H"), (3, "H"), (6, "A"), (7, "E"), (15, "G"), (40, "G"), (100, "A")],
]

OTHER = [0.5, 1.0, 3.0, 10.0, 5.0, 1.0, 2.0, 0.1, 1.5, 0.2, 1.0, 2.0, 0.1, 0.4, 2.0, 0.5, 2.5, 0.3, 0.2, 0.1, 0.4]

t0 = datetime(2026, 1, 1, 12, tzinfo=timezone.utc)
logs = [
    ReviewLog(card_id=item + 1, rating=GRADES[g][0], review_datetime=t0 + timedelta(days=day, minutes=i), review_duration=None)
    for item, history in enumerate(HISTORIES)
    for i, (day, g) in enumerate(history)
]
optimizer = Optimizer(logs)

def kotlin_list(values):
    return "listOf(" + ", ".join(repr(float(v)) for v in values) + ")"

print("package com.jatta.engine\n")
print("import com.jatta.engine.fsrs.Grade")
print("import com.jatta.engine.fsrs.Review\n")
print("object FsrsOptimizerVectors {")
print("    val histories = listOf(")
for history in HISTORIES:
    print("        listOf(" + ", ".join(f"Review({d}, Grade.{GRADES[g][1]})" for d, g in history) + "),")
print("    )")
print(f"    val other = {kotlin_list(OTHER)}")
print(f"    const val DEFAULT_LOSS = {optimizer._compute_batch_loss(parameters=list(DEFAULT_PARAMETERS))!r}")
print(f"    const val OTHER_LOSS = {optimizer._compute_batch_loss(parameters=OTHER)!r}")
print("}")
