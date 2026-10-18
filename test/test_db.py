"""Exercises the app's SQL (copied verbatim from Db.java) on SQLite: rate history, stock entries,
re-saves, opening carry-forward, and the aggregates behind KPIs, charts and reports.
Run: python3 test/test_db.py"""
import re, sqlite3, random
src = open('app/src/main/java/com/retail/trucksales/Db.java').read()
def strs(m):  # join Java string-literal concatenation into one SQL string
    return ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"', m))
d = sqlite3.connect(':memory:')
SALES_DDL = lambda name: strs(re.search(r'private static String salesTable.*?return (.*?);', src, re.S).group(1)).replace('" + name + "', '').replace('CREATE TABLE ', 'CREATE TABLE ' + name, 1)
for m in re.findall(r'd\.execSQL\(("CREATE[^;]*?)\);', src, re.S): d.execute(strs(m))
d.execute(SALES_DDL("sales"))
for i in range(1, 26):
    d.execute("INSERT INTO products VALUES(?,?)", (i, f"Product {i}")); d.execute("INSERT INTO rates VALUES(?,?,?)", (i, "2000-01-01", 0))
for i in range(1, 6): d.execute("INSERT INTO trucks VALUES(?,?,?)", (i, f"Truck {i}", f"Route {i}"))
RATE_SQL = strs(re.search(r'List<Entry> productsOn.*?String sql = (.*?);', src, re.S).group(1))
PREV_SQL = strs(re.search(r'if \(!saved\) \{\s*String sql = (.*?);', src, re.S).group(1))
STATUS_SQL = strs(re.search(r'Map<Integer, int\[\]> status.*?rawQuery\((.*?), new String', src, re.S).group(1))
MIGRATE = [strs(m) for m in re.findall(r'd\.execSQL\(("(?:INSERT INTO sales_new|DROP TABLE|ALTER TABLE)[^;]*?)\);', src, re.S)]
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
    for p, o, c in rows:  # c None = closing pending (morning save)
        q = 0 if c is None else r2(o - c); d.execute("INSERT INTO sales VALUES(?,?,?,?,?,?,?,?,?)", (date, truck, p, o, 0, c, q, rt[p], r2(q * rt[p])))
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

# ---- Morning / evening flow ----
save_rates("2026-11-01", {5: 40.0, 6: 25.0})
save_entry("2026-11-02", 3, [(5, 100, None), (6, 60, None)])          # morning: opening only
st = {r[0]: (r[1], r[2]) for r in d.execute(STATUS_SQL, ("2026-11-02",))}
eq("morning save: 2 products pending", (2, 2), st[3])
eq("pending rows add no sales", (0.0, 0.0), group("'t'", "2026-11-02", "2026-11-02")["t"])
last_done = {p: d.execute("SELECT closing FROM sales WHERE truck_id=3 AND product_id=? AND closing IS NOT NULL AND date<'2026-11-02' ORDER BY date DESC LIMIT 1", (p,)).fetchone()[0] for p in (5, 6)}
eq("pending day skipped; opening from last completed day", last_done, {r[0]: r[1] for r in d.execute(PREV_SQL, ("3", "2026-11-03")) if r[0] in (5, 6)})
save_entry("2026-11-02", 3, [(5, 100, 30), (6, 60, None)])            # evening: one closing entered
st = {r[0]: (r[1], r[2]) for r in d.execute(STATUS_SQL, ("2026-11-02",))}
eq("partial evening: 1 pending", (2, 1), st[3])
eq("partial evening value", 2800.0, group("'t'", "2026-11-02", "2026-11-02")["t"][1])   # 70*40
save_entry("2026-11-02", 3, [(5, 100, 30), (6, 60, 10)])              # evening complete
st = {r[0]: (r[1], r[2]) for r in d.execute(STATUS_SQL, ("2026-11-02",))}
eq("completed: 0 pending", (2, 0), st[3])
eq("completed value", 4050.0, group("'t'", "2026-11-02", "2026-11-02")["t"][1])         # 2800 + 50*25
eq("next morning opening = yesterday closing", {5: 30.0, 6: 10.0}, {r[0]: r[1] for r in d.execute(PREV_SQL, ("3", "2026-11-03")) if r[0] in (5, 6)})

# ---- Upgrade of existing v1 data (closing was NOT NULL) keeps every row ----
old = sqlite3.connect(':memory:')
old.execute("CREATE TABLE sales(date TEXT NOT NULL, truck_id INTEGER NOT NULL, product_id INTEGER NOT NULL, opening REAL NOT NULL, loaded REAL NOT NULL, closing REAL NOT NULL, qty REAL NOT NULL, rate REAL NOT NULL, value REAL NOT NULL, PRIMARY KEY(date, truck_id, product_id))")
old.execute("INSERT INTO sales VALUES('2026-10-09',1,1,100,0,40,60,30,1800)")
old.execute(SALES_DDL("sales_new"))
for q in MIGRATE: old.execute(q)
eq("upgrade keeps data", [('2026-10-09', 1, 1, 100.0, 0.0, 40.0, 60.0, 30.0, 1800.0)], old.execute("SELECT * FROM sales").fetchall())
old.execute("INSERT INTO sales VALUES('2026-10-10',1,1,40,0,NULL,0,30,0)")
eq("upgraded table accepts pending closing", 1, old.execute("SELECT COUNT(*) FROM sales WHERE closing IS NULL").fetchone()[0])
eq("upgrade steps found", 3, len(MIGRATE))

# ---- Backup check and CSV export queries ----
CHECK_SQL = strs(re.search(r't\.rawQuery\((.*?), null\)', src, re.S).group(1))
d.execute("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)") if not d.execute("SELECT 1 FROM sqlite_master WHERE name='settings'").fetchone() else None
eq("backup file recognised", 5, d.execute(CHECK_SQL).fetchone()[0])
eq("random file rejected", 0, sqlite3.connect(':memory:').execute(CHECK_SQL).fetchone()[0])
CSV_SQL = strs(re.search(r'void exportCsv.*?String sql = (.*?);', src, re.S).group(1))
rows = d.execute(CSV_SQL, ("2026-11-02", "2026-11-02")).fetchall()
eq("CSV rows for day", 2, len(rows)); eq("CSV sold column", 70.0, rows[0][6])

print("ALL DB TESTS PASSED" if not fails else f"{fails} FAILED")
