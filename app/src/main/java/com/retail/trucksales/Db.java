package com.retail.trucksales;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local SQLite database (stored on the phone, survives app close and restart).
 *
 * Rates are date-effective: each change is stored with the date it takes effect, so the rate for
 * any day is the latest rate with eff_date <= that day. Each sale row also stores the rate and
 * value used when it was saved, so later rate changes never alter recorded sales.
 */
final class Db extends SQLiteOpenHelper {
    static final int PRODUCTS = 25, TRUCKS = 5;

    static final class Truck { int id; String name, route; }

    static final class Entry {
        int pid; String name; double rate;
        Double opening, closing;
    }

    private static Db inst;

    static synchronized Db get(Context c) {
        if (inst == null) inst = new Db(c.getApplicationContext());
        return inst;
    }

    private Db(Context c) { super(c, "truck_sales.db", null, 1); }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        d.execSQL("CREATE TABLE rates(product_id INTEGER NOT NULL, eff_date TEXT NOT NULL, rate REAL NOT NULL,"
                + " PRIMARY KEY(product_id, eff_date))");
        d.execSQL("CREATE TABLE trucks(id INTEGER PRIMARY KEY, name TEXT NOT NULL, route TEXT NOT NULL)");
        d.execSQL("CREATE TABLE sales(date TEXT NOT NULL, truck_id INTEGER NOT NULL, product_id INTEGER NOT NULL,"
                + " opening REAL NOT NULL, loaded REAL NOT NULL, closing REAL NOT NULL, qty REAL NOT NULL,"
                + " rate REAL NOT NULL, value REAL NOT NULL, PRIMARY KEY(date, truck_id, product_id))");
        d.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)");
        for (int i = 1; i <= PRODUCTS; i++) {
            d.execSQL("INSERT INTO products VALUES(?,?)", new Object[]{i, "Product " + i});
            d.execSQL("INSERT INTO rates VALUES(?,?,?)", new Object[]{i, "2000-01-01", 0});
        }
        for (int i = 1; i <= TRUCKS; i++)
            d.execSQL("INSERT INTO trucks VALUES(?,?,?)", new Object[]{i, "Truck " + i, "Route " + i});
        d.execSQL("INSERT INTO settings VALUES('pin','1234')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {}

    // ---------------- settings ----------------

    String setting(String k, String def) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM settings WHERE k=?", new String[]{k})) {
            return c.moveToFirst() ? c.getString(0) : def;
        }
    }

    void setSetting(String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        getWritableDatabase().insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---------------- trucks ----------------

    List<Truck> trucks() {
        List<Truck> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,route FROM trucks ORDER BY id", null)) {
            while (c.moveToNext()) {
                Truck t = new Truck();
                t.id = c.getInt(0); t.name = c.getString(1); t.route = c.getString(2);
                out.add(t);
            }
        }
        return out;
    }

    void saveTrucks(List<Truck> ts) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            for (Truck t : ts)
                d.execSQL("UPDATE trucks SET name=?, route=? WHERE id=?", new Object[]{t.name, t.route, t.id});
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }

    // ---------------- products & rates ----------------

    /** All products with the rate applicable on the given date. */
    List<Entry> productsOn(String date) {
        List<Entry> out = new ArrayList<>();
        String sql = "SELECT p.id, p.name, IFNULL((SELECT r.rate FROM rates r WHERE r.product_id=p.id"
                + " AND r.eff_date<=? ORDER BY r.eff_date DESC LIMIT 1),0) FROM products p ORDER BY p.id";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{date})) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.pid = c.getInt(0); e.name = c.getString(1); e.rate = c.getDouble(2);
                out.add(e);
            }
        }
        return out;
    }

    /** Saves changed rates effective from {@code date} and renamed products. */
    void saveRates(String date, Map<Integer, Double> rates, Map<Integer, String> names) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            for (Map.Entry<Integer, Double> r : rates.entrySet())
                d.execSQL("INSERT OR REPLACE INTO rates VALUES(?,?,?)", new Object[]{r.getKey(), date, r.getValue()});
            for (Map.Entry<Integer, String> n : names.entrySet())
                d.execSQL("UPDATE products SET name=? WHERE id=?", new Object[]{n.getValue(), n.getKey()});
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }

    // ---------------- daily truck entries ----------------

    /**
     * Rows for the entry screen. Saved figures are loaded if the day was already entered;
     * otherwise opening stock is pre-filled with that truck's last closing stock.
     */
    List<Entry> loadEntry(String date, int truck) {
        List<Entry> list = productsOn(date);
        Map<Integer, Entry> byId = new HashMap<>();
        for (Entry e : list) byId.put(e.pid, e);
        String t = String.valueOf(truck);
        boolean saved = false;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT product_id,opening,loaded,closing FROM sales WHERE date=? AND truck_id=?",
                new String[]{date, t})) {
            while (c.moveToNext()) {
                Entry e = byId.get(c.getInt(0));
                if (e == null) continue;
                e.opening = c.getDouble(1); e.closing = c.getDouble(3);
                saved = true;
            }
        }
        if (!saved) {
            String sql = "SELECT s.product_id, s.closing FROM sales s WHERE s.truck_id=? AND s.date="
                    + "(SELECT MAX(x.date) FROM sales x WHERE x.truck_id=s.truck_id AND x.product_id=s.product_id AND x.date<?)";
            try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{t, date})) {
                while (c.moveToNext()) {
                    Entry e = byId.get(c.getInt(0));
                    if (e != null && c.getDouble(1) > 0) e.opening = c.getDouble(1);
                }
            }
        }
        return list;
    }

    /** Replaces the day's entry for one truck. Each row: {productId, opening, closing, rate}. Sold = opening - closing. */
    void saveEntry(String date, int truck, List<double[]> rows) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            d.execSQL("DELETE FROM sales WHERE date=? AND truck_id=?", new Object[]{date, truck});
            for (double[] r : rows) {
                double q = Calc.sold(r[1], r[2]);
                d.execSQL("INSERT INTO sales VALUES(?,?,?,?,?,?,?,?,?)", new Object[]{date, truck, (int) r[0],
                        r[1], 0, r[2], q, r[3], Calc.value(q, r[3])});
            }
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }

    // ---------------- reports ----------------

    /** key -> {qty, value} for sales between two dates (inclusive). key is a fixed SQL expression. */
    LinkedHashMap<String, double[]> group(String key, String from, String to) {
        LinkedHashMap<String, double[]> out = new LinkedHashMap<>();
        String sql = "SELECT " + key + " AS k, SUM(qty), SUM(value) FROM sales WHERE date BETWEEN ? AND ?"
                + " GROUP BY k ORDER BY k";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{from, to})) {
            while (c.moveToNext())
                out.put(c.getString(0), new double[]{Calc.round2(c.getDouble(1)), Calc.round2(c.getDouble(2))});
        }
        return out;
    }

    double[] total(String from, String to) {
        double[] t = group("'t'", from, to).get("t");
        return t == null ? new double[]{0, 0} : t;
    }

    /** Detail lines for one day: truck, product, opening, (unused), closing, qty, rate, value. */
    List<String[]> dayDetail(String date) {
        List<String[]> out = new ArrayList<>();
        String sql = "SELECT t.name, p.name, s.opening, s.loaded, s.closing, s.qty, s.rate, s.value FROM sales s"
                + " JOIN trucks t ON t.id=s.truck_id JOIN products p ON p.id=s.product_id"
                + " WHERE s.date=? ORDER BY s.truck_id, s.product_id";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{date})) {
            while (c.moveToNext()) {
                String[] r = new String[8];
                r[0] = c.getString(0); r[1] = c.getString(1);
                r[2] = Ui.qty(c.getDouble(2)); r[3] = Ui.qty(c.getDouble(3)); r[4] = Ui.qty(c.getDouble(4));
                r[5] = Ui.qty(c.getDouble(5)); r[6] = Ui.money(c.getDouble(6)); r[7] = Ui.money(c.getDouble(7));
                out.add(r);
            }
        }
        return out;
    }
}
