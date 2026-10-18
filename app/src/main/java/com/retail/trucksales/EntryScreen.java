package com.retail.trucksales;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Daily stock entry for one truck: opening and closing per product; sold and value are automatic. */
final class EntryScreen extends Screen {
    private static int truck = 1; // remembered while the app is open
    private List<Db.Entry> rows;
    private final List<EditText[]> inputs = new ArrayList<>();
    private final List<TextView> outs = new ArrayList<>();
    private TextView total;
    private boolean dirty, loading;

    EntryScreen(MainActivity a) { super(a); }

    @Override
    boolean dirty() { return dirty; }

    @Override
    View build() {
        LinearLayout root = Ui.box(a, true);
        int pad = Ui.dp(a, 8);
        root.setPadding(pad, pad, pad, 0);

        // Truck selector
        List<Db.Truck> trucks = db.trucks();
        LinearLayout sel = Ui.box(a, false);
        String route = "";
        for (Db.Truck t : trucks) {
            Button b = Ui.button(a, t.name, t.id == truck);
            if (t.id != truck) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(Ui.CARD);
                g.setStroke(1, Ui.LINE);
                g.setCornerRadius(Ui.dp(a, 8));
                b.setBackground(g);
                b.setTextColor(Ui.TEXT);
            } else route = t.route;
            b.setTextSize(13);
            b.setOnClickListener(v -> a.confirmLeave(() -> { truck = t.id; a.render(); }));
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(2, 0, 2, 0);
            sel.addView(b, lp);
        }
        root.addView(sel);
        TextView rt = Ui.text(a, "Route: " + route, 13, Ui.MUTED, false);
        rt.setPadding(Ui.dp(a, 4), Ui.dp(a, 6), 0, Ui.dp(a, 6));
        root.addView(rt);

        // Header
        LinearLayout head = Ui.box(a, false);
        head.addView(cell("Product @ rate", Gravity.START), Ui.weight(1.5f));
        head.addView(cell("Opening", Gravity.CENTER), Ui.weight(1));
        head.addView(cell("Closing", Gravity.CENTER), Ui.weight(1));
        head.addView(cell("Sold / ₹", Gravity.END), Ui.weight(1.3f));
        root.addView(head);

        // Rows
        LinearLayout list = Ui.box(a, true);
        rows = db.loadEntry(a.date, truck);
        loading = true;
        for (int i = 0; i < rows.size(); i++) {
            Db.Entry e = rows.get(i);
            LinearLayout r = Ui.box(a, false);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackgroundColor(i % 2 == 0 ? Ui.CARD : Ui.BG);
            TextView name = Ui.text(a, e.name + "\n@ " + Ui.money(e.rate), 13, Ui.TEXT, false);
            name.setPadding(Ui.dp(a, 4), 0, 0, 0);
            r.addView(name, Ui.weight(1.5f));
            EditText[] f = {Ui.num(a), Ui.num(a)};
            f[0].setText(Ui.plain(e.opening));
            f[1].setText(Ui.plain(e.closing));
            final int idx = i;
            for (EditText t : f) {
                t.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
                    public void onTextChanged(CharSequence s, int st, int b, int c) {}
                    public void afterTextChanged(Editable s) {
                        if (!loading) dirty = true;
                        recalc(idx);
                    }
                });
                r.addView(t, Ui.weight(1));
            }
            TextView out = Ui.text(a, "", 13, Ui.TEXT, false);
            out.setGravity(Gravity.END);
            out.setPadding(0, 0, Ui.dp(a, 4), 0);
            r.addView(out, Ui.weight(1.3f));
            inputs.add(f);
            outs.add(out);
            list.addView(r);
        }
        ScrollView sv = Ui.scroll(a, list);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        for (int i = 0; i < rows.size(); i++) recalc(i);
        loading = false;

        // Bottom: total and save
        LinearLayout bottom = Ui.box(a, false);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        total = Ui.text(a, "", 15, Ui.TEXT, true);
        bottom.addView(total, Ui.weight(1));
        Button save = Ui.button(a, "  Save  ", true);
        save.setOnClickListener(v -> save());
        bottom.addView(save);
        root.addView(bottom);
        updateTotal();
        return root;
    }

    private TextView cell(String s, int gravity) {
        TextView t = Ui.text(a, s, 12, Ui.MUTED, true);
        t.setGravity(gravity);
        return t;
    }

    private static double nz(Double d) { return d == null ? 0 : d; }

    /** Returns {qty, value} for a row, or null if closing is blank / invalid. */
    private double[] calc(int i) {
        try {
            EditText[] f = inputs.get(i);
            Double o = Ui.parse(f[0]), c = Ui.parse(f[1]);
            if (c == null || Calc.check(nz(o), c) != null) return null;
            double q = Calc.sold(nz(o), c);
            return new double[]{q, Calc.value(q, rows.get(i).rate)};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void recalc(int i) {
        TextView out = outs.get(i);
        double[] r = calc(i);
        if (r != null) {
            out.setTextColor(Ui.TEXT);
            out.setText(Ui.qty(r[0]) + "\n" + Ui.money(r[1]));
        } else {
            boolean blank = inputs.get(i)[1].getText().toString().trim().isEmpty();
            out.setTextColor(blank ? Ui.MUTED : Ui.RED);
            out.setText(blank ? "–" : "Check");
        }
        if (total != null) updateTotal();
    }

    private void updateTotal() {
        double q = 0, v = 0;
        for (int i = 0; i < rows.size(); i++) {
            double[] r = calc(i);
            if (r != null) { q += r[0]; v += r[1]; }
        }
        total.setText("Sold " + Ui.qty(Calc.round2(q)) + "  |  " + Ui.money(Calc.round2(v)));
    }

    private void save() {
        List<double[]> data = new ArrayList<>();
        boolean zeroRate = false;
        for (int i = 0; i < rows.size(); i++) {
            Db.Entry e = rows.get(i);
            Double o, c;
            try {
                EditText[] f = inputs.get(i);
                o = Ui.parse(f[0]); c = Ui.parse(f[1]);
            } catch (NumberFormatException ex) {
                error(i, e.name + ": please enter a valid number.");
                return;
            }
            if (nz(o) == 0 && nz(c) == 0) continue; // product not carried today
            if (c == null) {
                error(i, e.name + ": please enter closing stock.");
                return;
            }
            String err = Calc.check(nz(o), c);
            if (err != null) {
                error(i, e.name + ": " + err + ".");
                return;
            }
            if (e.rate == 0 && Calc.sold(nz(o), c) > 0) zeroRate = true;
            data.add(new double[]{e.pid, nz(o), c, e.rate});
        }
        Runnable doSave = () -> {
            db.saveEntry(a.date, truck, data);
            dirty = false;
            a.toast("Saved");
            a.render();
        };
        if (zeroRate) {
            new AlertDialog.Builder(a).setTitle("Rate not set")
                    .setMessage("Some sold products have rate ₹0. Set rates in Settings first, or save anyway?")
                    .setPositiveButton("Save anyway", (d, w) -> doSave.run())
                    .setNegativeButton("Cancel", null).show();
        } else doSave.run();
    }

    private void error(int i, String msg) {
        new AlertDialog.Builder(a).setTitle("Please check").setMessage(msg).setPositiveButton("OK", null).show();
        inputs.get(i)[1].requestFocus();
    }
}
