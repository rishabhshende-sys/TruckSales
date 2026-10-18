package com.retail.trucksales;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Map;

/** Date-range report: summary, date-wise (tap a day for full detail), truck-wise and product-wise. */
final class ReportScreen extends Screen {
    private static String from, to; // remembered while the app is open

    ReportScreen(MainActivity a) { super(a); }

    @Override
    View build() {
        if (from == null) { from = a.date.substring(0, 7) + "-01"; to = a.date; }
        if (from.compareTo(to) > 0) { String t = from; from = to; to = t; }
        LinearLayout p = Ui.page(a);

        LinearLayout range = Ui.box(a, false);
        Button bf = Ui.button(a, "From: " + Calc.shortDate(from), false);
        Button bt = Ui.button(a, "To: " + Calc.shortDate(to), false);
        bf.setOnClickListener(v -> a.pickDate(from, d -> { from = d; a.render(); }));
        bt.setOnClickListener(v -> a.pickDate(to, d -> { to = d; a.render(); }));
        range.addView(bf, Ui.weight(1));
        range.addView(bt, Ui.weight(1));
        p.addView(range);
        Button month = Ui.button(a, "This month (" + Calc.monthName(a.date.substring(0, 7), true) + ")", false);
        month.setOnClickListener(v -> {
            from = a.date.substring(0, 7) + "-01";
            to = a.date.substring(0, 7) + "-" + Calc.daysInMonth(a.date.substring(0, 7));
            a.render();
        });
        p.addView(month, Ui.fullWidth());

        Map<String, double[]> byDate = db.group("date", from, to);
        double[] tot = db.total(from, to);
        LinearLayout s = Ui.card(a, "Summary");
        s.addView(Ui.row(a, false, "Total sales value", Ui.money(tot[1])));
        s.addView(Ui.row(a, false, "Total quantity sold", Ui.qty(tot[0])));
        s.addView(Ui.row(a, false, "Days with sales", String.valueOf(byDate.size())));
        p.addView(s);

        LinearLayout dc = Ui.card(a, "Date-wise (tap a date for details)");
        dc.addView(Ui.row(a, true, "Date", "Qty", "Value"));
        for (Map.Entry<String, double[]> e : byDate.entrySet()) {
            LinearLayout r = Ui.row(a, false, Calc.shortDate(e.getKey()), Ui.qty(e.getValue()[0]), Ui.money(e.getValue()[1]));
            r.setOnClickListener(v -> showDay(e.getKey()));
            dc.addView(Ui.divider(a));
            dc.addView(r);
        }
        if (byDate.isEmpty()) dc.addView(Ui.text(a, "No sales in this period", 14, Ui.MUTED, false));
        p.addView(dc);

        Map<String, double[]> byTruck = db.group("truck_id", from, to);
        LinearLayout tc = Ui.card(a, "Truck-wise");
        tc.addView(Ui.row(a, true, "Truck", "Qty", "Value"));
        for (Db.Truck t : db.trucks()) {
            double[] v = byTruck.get(String.valueOf(t.id));
            tc.addView(Ui.divider(a));
            tc.addView(Ui.row(a, false, t.name, Ui.qty(v == null ? 0 : v[0]), Ui.money(v == null ? 0 : v[1])));
        }
        p.addView(tc);

        Map<String, double[]> byProduct = db.group("product_id", from, to);
        LinearLayout pc = Ui.card(a, "Product-wise");
        pc.addView(Ui.row(a, true, "Product", "Qty", "Value"));
        for (Db.Entry e : db.productsOn(to)) {
            double[] v = byProduct.get(String.valueOf(e.pid));
            if (v == null) continue;
            pc.addView(Ui.divider(a));
            pc.addView(Ui.row(a, false, e.name, Ui.qty(v[0]), Ui.money(v[1])));
        }
        p.addView(pc);
        return Ui.scroll(a, p);
    }

    private void showDay(String date) {
        LinearLayout l = Ui.page(a);
        String lastTruck = null;
        for (String[] r : db.dayDetail(date)) {
            if (!r[0].equals(lastTruck)) {
                lastTruck = r[0];
                l.addView(Ui.text(a, "\n" + r[0], 15, Ui.PRIMARY, true));
            }
            l.addView(Ui.text(a, r[1] + "\nOpen " + r[2] + " − Close " + r[4]
                    + " = " + r[5] + " × " + r[6] + " = " + r[7], 13, Ui.TEXT, false));
            l.addView(Ui.divider(a));
        }
        double[] t = db.total(date, date);
        l.addView(Ui.text(a, "\nDay total: qty " + Ui.qty(t[0]) + ", " + Ui.money(t[1]), 15, Ui.TEXT, true));
        new AlertDialog.Builder(a).setTitle(Calc.nice(date)).setView(Ui.scroll(a, l))
                .setPositiveButton("Close", null).show();
    }
}
