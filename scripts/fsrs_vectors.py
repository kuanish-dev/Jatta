from datetime import datetime, timedelta, timezone
from fsrs import Scheduler, Card, Rating

R = {"A": Rating.Again, "H": Rating.Hard, "G": Rating.Good, "E": Rating.Easy}

def run(name, steps, retention=0.9):
    s = Scheduler(learning_steps=(), relearning_steps=(), enable_fuzzing=False, desired_retention=retention)
    card = Card()
    t0 = datetime(2026, 1, 1, tzinfo=timezone.utc)
    t, day = t0, 0
    rows = []
    for i, (r, delay) in enumerate(steps):
        if i > 0:
            d = (card.due - t).days if delay == "due" else delay
            t = t + timedelta(days=d); day += d
        card, _ = s.review_card(card, R[r], t)
        ivl = (card.due - t).days
        rows.append(f'        Step({day}, Grade.{ {"A":"Again","H":"Hard","G":"Good","E":"Easy"}[r]}, {card.stability!r}, {card.difficulty!r}, {ivl}),')
    print(f'    val {name} = Case({retention}, listOf(')
    print("\n".join(rows))
    print("    ))")

run("allGood", [("G", "due")] * 8)
run("mixed", [("A","due"),("G","due"),("G","due"),("A","due"),("G","due"),("H","due"),("E","due"),("G","due")])
run("easyThenLapses", [("E","due"),("E","due"),("A","due"),("A","due"),("G","due"),("G","due")])
run("offSchedule", [("G","due"),("G",10),("G",2),("H",30),("G","due"),("A",1),("G","due")])
run("lowRetention", [("G","due")] * 6, retention=0.85)

