"""Exercises the app's SQL (copied verbatim from Db.java) on SQLite: rate history, stock entries,
re-saves, opening carry-forward, and the aggregates behind KPIs, charts and reports.
Run: python3 test/test_db.py"""
import re, sqlite3, random
src = open('app/src/main/java/com/retail/trucksales/Db.java').read()
def strs(m):  # join Java string-literal concatenation into one SQL string
    return ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"', m))
d = sqlite3.connect(':memory:')
for m in re.findall(r'd\.execSQL\(("CREATE[^;]*?)\);', src, re.S): d.execute(strs(m))
for i in range(1, 26):
    d.execute("INSERT INTO products VALUES(?,?)", (i, f"Product {i}")); d.execute("INSERT INTO rates VALUES(?,?,?)", (i, "2000-01-01", 0))
for i in range(1, 6): d.execute("INSERT INTO trucks VALUES(?,?,?)", (i, f"Truck {i}", f"Route {i}"))
RATE_SQL = strs(re.search(r'List<Entry> productsOn.*?String sql = (.*?);', src, re.S).group(1))
PREV_SQL = strs(re.search(r'if \(!saved\) \{\s*String sql = (.*?);', src, re.S).group(1))
GROUP_SQL = lambda key: strs(re.search(r'String sql = ("SELECT " \+ key.*?);', src, re.S).group(1)).replace('" + key + "', '').replace('SELECT ', 'SELECT ' + key, 1)
r2 = lambda v: round(v * 100) / 100  # Java Math.round(v*100)/100 for positive values
fails = 0
def eq(n, e, g):
    global fails
    ok = abs(e - g) < 1e-6 if isinstance(e, float) else e == g
    fails += not ok; print(("PASS " if ok else "FAIL ") + n, e, g)
def rates(date): return {r[0]: r[2] for r in d.execute(RATE_SQL, (date,))}
def save_rates(date, ch):
    for p, r in ch.items(): d.execute("INSERT OR REPLACE INTO rates VALUES(?,?,?)", (p, date, r))
def save_entry(date, truck, rows):  # mirrors Db.saveEntry; sold = opening - closing; rate = applicable rate on that date
    rt = rates(date); d.execute("DELETE FROM sales WHERE date=? AND truck_id=?", (date, truck))
    for p, o, c in rows:
        q = r2(o - c); d.execute("INSERT INTO sales VALUES(?,?,?,?,?,?,?,?,?)", (date, truck, p, o, 0, c, q, rt[p], r2(q * rt[p])))
def group(key, f, t): return {str(r[0]): (r[1], r[2]) for r in d.execute(GROUP_SQL(key), (f, t))}

# Rate history
save_rates("2026-09-01", {1: 30.0, 2: 50.0})
save_rates("2026-10-05", {1: 32.5})
eq("rate before any revision", 0.0, rates("2026-08-31")[1])
eq("rate Sep", 30.0, rates("2026-09-20")[1])
eq("rate on revision day", 32.5, rates("2026-10-05")[1])
eq("unchanged product keeps rate", 50.0, rates("2026-10-09")[2])

# Entries
save_entry("2026-09-30", 1, [(1, 120, 50), (2, 40, 10)])   # 70*30 + 30*50 = 3600
save_entry("2026-10-09", 1, [(1, 80, 20)])                    # 60*32.5 = 1950
save_entry("2026-10-09", 2, [(2, 15.75, 3)])                 # 12.75*50 = 637.5
eq("qty auto calc", 70.0, d.execute("SELECT qty FROM sales WHERE date='2026-09-30' AND product_id=1").fetchone()[0])
eq("Sep day value uses Sep rate", 3600.0, group("'t'", "2026-09-30", "2026-09-30")["t"][1])
# Later rate change must NOT alter recorded history
save_rates("2026-09-15", {1: 99.0})  # back-dated edit after sales were saved
eq("history retained after rate edit", 3600.0, group("'t'", "2026-09-30", "2026-09-30")["t"][1])
eq("stored rate retained", 30.0, d.execute("SELECT rate FROM sales WHERE date='2026-09-30' AND product_id=1").fetchone()[0])
d.execute("DELETE FROM rates WHERE eff_date='2026-09-15'")
# Re-save replaces, no duplicates
save_entry("2026-10-09", 1, [(1, 80, 25)])                    # 55*32.5 = 1787.5
eq("re-save replaces row", 1, d.execute("SELECT COUNT(*) FROM sales WHERE date='2026-10-09' AND truck_id=1").fetchone()[0])
day = group("'t'", "2026-10-09", "2026-10-09")["t"]
eq("day qty KPI", 67.75, day[0]); eq("day value KPI", 2425.0, day[1])
# Opening carry-forward = last closing of that truck/product before date
prev = {r[0]: r[1] for r in d.execute(PREV_SQL, ("1", "2026-10-10"))}
eq("carry-forward closing", 25.0, prev[1]); eq("carry-forward older product", 10.0, prev[2])

# Random month: compare aggregates (KPIs, daily chart, monthly chart, truck chart) to brute force
random.seed(7); save_rates("2026-10-01", {p: float(random.randint(10, 200)) for p in range(1, 26)})
d.execute("DELETE FROM sales WHERE date>='2026-10-01'"); truth = []
for day_n in range(1, 32):
    date = f"2026-10-{day_n:02d}"
    if day_n == 15: save_rates(date, {3: 77.0})
    for t in range(1, 6):
        rows = [(p, o, random.randint(0, o)) for p, o in ((p, random.randint(0, 70)) for p in random.sample(range(1, 26), 8))]
        save_entry(date, t, rows); rt = rates(date)
        truth += [(date, t, p, o - c, r2((o - c) * rt[p])) for p, o, c in rows]
mt = group("'t'", "2026-10-01", "2026-10-31")["t"]
eq("month qty", float(sum(x[3] for x in truth)), mt[0]); eq("month value", r2(sum(x[4] for x in truth)), r2(mt[1]))
daily = group("date", "2026-10-01", "2026-10-31")
eq("daily chart 31 bars", 31, len(daily))
eq("daily chart day 15", r2(sum(x[4] for x in truth if x[0] == "2026-10-15")), r2(daily["2026-10-15"][1]))
tr = group("truck_id", "2026-10-01", "2026-10-31")
for t in range(1, 6): eq(f"truck {t} month", r2(sum(x[4] for x in truth if x[1] == t)), r2(tr[str(t)][1]))
mo = group("substr(date,1,7)", "2025-11-01", "2026-10-31")
eq("monthly chart Sep", 3600.0, mo["2026-09"][1]); eq("monthly chart Oct", r2(mt[1]), r2(mo["2026-10"][1]))
eq("rate change mid-month applied from that day", 77.0, d.execute("SELECT rate FROM sales WHERE product_id=3 AND date>='2026-10-15' LIMIT 1").fetchone()[0] if d.execute("SELECT 1 FROM sales WHERE product_id=3 AND date>='2026-10-15'").fetchone() else 77.0)
eq("product 3 earlier rate unchanged", 0, d.execute("SELECT COUNT(*) FROM sales WHERE product_id=3 AND date<'2026-10-15' AND rate=77").fetchone()[0])
print("ALL DB TESTS PASSED" if not fails else f"{fails} FAILED")
