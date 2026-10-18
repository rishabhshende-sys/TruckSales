package com.retail.trucksales;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** PIN-protected: daily rate revision, product names, truck names / routes, PIN change. */
final class SettingsScreen extends Screen {
    SettingsScreen(MainActivity a) { super(a); }

    @Override
    View build() {
        LinearLayout p = Ui.page(a);

        // Rates effective from the selected date
        LinearLayout rc = Ui.card(a, "Rates effective from " + Calc.nice(a.date));
        rc.addView(Ui.text(a, "Change the date at the top to revise rates for another day. New rates apply to "
                + "entries saved for this date onwards. Sales already saved keep the rate they were recorded with.",
                12, Ui.MUTED, false));
        List<Db.Entry> products = db.productsOn(a.date);
        List<EditText[]> fields = new ArrayList<>();
        for (Db.Entry e : products) {
            LinearLayout r = Ui.box(a, false);
            EditText name = Ui.field(a, e.name);
            EditText rate = Ui.num(a);
            rate.setText(Ui.plain(e.rate));
            r.addView(name, Ui.weight(2));
            r.addView(rate, Ui.weight(1));
            rc.addView(r);
            fields.add(new EditText[]{name, rate});
        }
        Button saveRates = Ui.button(a, "Save Rates", true);
        saveRates.setOnClickListener(v -> {
            Map<Integer, Double> rates = new HashMap<>();
            Map<Integer, String> names = new HashMap<>();
            for (int i = 0; i < products.size(); i++) {
                Db.Entry e = products.get(i);
                String n = fields.get(i)[0].getText().toString().trim();
                Double r;
                try { r = Ui.parse(fields.get(i)[1]); } catch (NumberFormatException ex) { r = -1.0; }
                if (r == null || r < 0 || n.isEmpty()) {
                    a.toast("Check name / rate of product " + e.pid);
                    return;
                }
                if (!n.equals(e.name)) names.put(e.pid, n);
                if (Math.abs(r - e.rate) > 1e-9) rates.put(e.pid, Calc.round2(r));
            }
            db.saveRates(a.date, rates, names);
            a.toast(rates.size() + " rate(s) updated");
            a.render();
        });
        rc.addView(saveRates, Ui.fullWidth());
        p.addView(rc);

        // Trucks and routes
        LinearLayout tc = Ui.card(a, "Trucks and village routes");
        List<Db.Truck> trucks = db.trucks();
        List<EditText[]> tf = new ArrayList<>();
        for (Db.Truck t : trucks) {
            LinearLayout r = Ui.box(a, false);
            EditText name = Ui.field(a, t.name), route = Ui.field(a, t.route);
            name.setHint("Truck name");
            route.setHint("Villages on route");
            r.addView(name, Ui.weight(1));
            r.addView(route, Ui.weight(2));
            tc.addView(r);
            tf.add(new EditText[]{name, route});
        }
        Button saveTrucks = Ui.button(a, "Save Trucks", true);
        saveTrucks.setOnClickListener(v -> {
            for (int i = 0; i < trucks.size(); i++) {
                String n = tf.get(i)[0].getText().toString().trim();
                if (n.isEmpty()) { a.toast("Truck name cannot be empty"); return; }
                trucks.get(i).name = n;
                trucks.get(i).route = tf.get(i)[1].getText().toString().trim();
            }
            db.saveTrucks(trucks);
            a.toast("Trucks saved");
            a.render();
        });
        tc.addView(saveTrucks, Ui.fullWidth());
        p.addView(tc);

        // PIN
        LinearLayout sc = Ui.card(a, "Security");
        Button pin = Ui.button(a, "Change PIN", false);
        pin.setOnClickListener(v -> {
            EditText e = new EditText(a);
            e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            e.setHint("New PIN (4+ digits)");
            new AlertDialog.Builder(a).setTitle("Change PIN").setView(e)
                    .setPositiveButton("Save", (d, w) -> {
                        String s = e.getText().toString();
                        if (s.length() < 4) { a.toast("PIN must be at least 4 digits"); return; }
                        db.setSetting("pin", s);
                        a.toast("PIN changed");
                    })
                    .setNegativeButton("Cancel", null).show();
        });
        sc.addView(pin, Ui.fullWidth());
        p.addView(sc);
        return Ui.scroll(a, p);
    }
}
