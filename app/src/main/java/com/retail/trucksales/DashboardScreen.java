package com.retail.trucksales;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

/** KPI cards, truck performance, daily / monthly / truck-wise charts for the selected date. */
final class DashboardScreen extends Screen {
    DashboardScreen(MainActivity a) { super(a); }

    @Override
    View build() {
        String day = a.date, month = day.substring(0, 7);
        String ms = month + "-01", me = month + "-31";
        String mName = Calc.monthName(month, true);
        LinearLayout p = Ui.page(a);

        // Trucks still out (morning stock saved, closing not entered yet)
        StringBuilder pend = new StringBuilder();
        Map<Integer, int[]> st = db.status(day);
        for (Db.Truck t : db.trucks()) {
            int[] x = st.get(t.id);
            if (x != null && x[1] > 0) pend.append(pend.length() > 0 ? ", " : "").append(t.name);
        }
        if (pend.length() > 0) {
            LinearLayout pc = Ui.card(a, null);
            pc.addView(Ui.text(a, "Closing stock pending: " + pend + "\nSales for these trucks are added once closing is entered.",
                    13, 0xFFE65100, true));
            p.addView(pc);
        }
        String lastBackup = db.setting("last_backup", "");
        if (lastBackup.isEmpty() || lastBackup.compareTo(Calc.addDays(Calc.today(), -7)) < 0) {
            LinearLayout bc = Ui.card(a, null);
            bc.addView(Ui.text(a, lastBackup.isEmpty() ? "No backup yet. Settings → Backup now."
                    : "Last backup " + Calc.shortDate(lastBackup) + ". Take a new one: Settings → Backup now.", 13, Ui.RED, false));
            p.addView(bc);
        }

        double[] dt = db.total(day, day), mt = db.total(ms, me);
        p.addView(pair(kpi("Sales on " + Calc.shortDate(day), Ui.money(dt[1]), Ui.PRIMARY),
                kpi("Sales in " + mName, Ui.money(mt[1]), Ui.GREEN)));
        p.addView(pair(kpi("Qty sold on " + Calc.shortDate(day), Ui.qty(dt[0]), Ui.TEXT),
                kpi("Qty sold in " + mName, Ui.qty(mt[0]), Ui.TEXT)));

        // Truck performance
        List<Db.Truck> trucks = db.trucks();
        Map<String, double[]> tDay = db.group("truck_id", day, day), tMonth = db.group("truck_id", ms, me);
        LinearLayout tc = Ui.card(a, "Truck Performance");
        tc.addView(Ui.row(a, true, "Truck / Route", "Day ₹", "Month ₹"));
        String[] tl = new String[trucks.size()];
        double[] tv = new double[trucks.size()];
        for (int i = 0; i < trucks.size(); i++) {
            Db.Truck t = trucks.get(i);
            double[] d = tDay.get(String.valueOf(t.id)), m = tMonth.get(String.valueOf(t.id));
            tc.addView(Ui.divider(a));
            tc.addView(Ui.row(a, false, t.name + "\n" + t.route,
                    Ui.money(d == null ? 0 : d[1]), Ui.money(m == null ? 0 : m[1])));
            tl[i] = t.name.length() > 9 ? t.name.substring(0, 9) : t.name;
            tv[i] = m == null ? 0 : m[1];
        }
        p.addView(tc);

        // Daily sales in the month
        int n = Calc.daysInMonth(month);
        Map<String, double[]> daily = db.group("date", ms, me);
        String[] dl = new String[n];
        double[] dv = new double[n];
        for (int i = 0; i < n; i++) {
            String key = String.format(java.util.Locale.US, "%s-%02d", month, i + 1);
            dl[i] = String.valueOf(i + 1);
            double[] v = daily.get(key);
            dv[i] = v == null ? 0 : v[1];
        }
        p.addView(chart("Daily Sales (₹) – " + mName, dl, dv));

        // Monthly sales, last 12 months
        String first = Calc.addMonths(month, -11);
        Map<String, double[]> monthly = db.group("substr(date,1,7)", first + "-01", me);
        String[] ml = new String[12];
        double[] mv = new double[12];
        for (int i = 0; i < 12; i++) {
            String m = Calc.addMonths(first, i);
            ml[i] = Calc.monthName(m, false);
            double[] v = monthly.get(m);
            mv[i] = v == null ? 0 : v[1];
        }
        p.addView(chart("Monthly Sales (₹) – last 12 months", ml, mv));

        p.addView(chart("Truck-wise Sales (₹) – " + mName, tl, tv));
        return Ui.scroll(a, p);
    }

    private LinearLayout chart(String title, String[] labels, double[] values) {
        LinearLayout c = Ui.card(a, title);
        ChartView v = new ChartView(a);
        v.setData(labels, values);
        c.addView(v);
        return c;
    }

    private LinearLayout kpi(String label, String value, int color) {
        LinearLayout c = Ui.card(a, null);
        c.addView(Ui.text(a, label, 12, Ui.MUTED, false));
        TextView v = Ui.text(a, value, 19, color, true);
        v.setPadding(0, Ui.dp(a, 4), 0, 0);
        c.addView(v);
        return c;
    }

    private LinearLayout pair(LinearLayout l, LinearLayout r) {
        LinearLayout row = Ui.box(a, false);
        LinearLayout.LayoutParams lp = Ui.weight(1);
        lp.setMargins(0, 0, Ui.dp(a, 5), 0);
        row.addView(l, lp);
        LinearLayout.LayoutParams rp = Ui.weight(1);
        rp.setMargins(Ui.dp(a, 5), 0, 0, 0);
        row.addView(r, rp);
        return row;
    }
}
