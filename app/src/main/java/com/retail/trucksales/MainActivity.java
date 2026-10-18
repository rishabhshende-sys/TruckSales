package com.retail.trucksales;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Locale;

/** Single screen app: date bar on top, content in the middle, 4 tabs at the bottom. */
public class MainActivity extends Activity {
    String date = Calc.today();
    private FrameLayout content;
    private TextView dateText;
    private final TextView[] tabs = new TextView[4];
    private int tab = 0;
    private Screen screen;
    private boolean unlocked = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true); // never draw under the status / navigation bars

        // Header: app name, then  ◀  date (tap to pick)  ▶  Today
        LinearLayout head = Ui.box(this, true);
        head.setBackgroundColor(Ui.PRIMARY);
        int p = Ui.dp(this, 8);
        head.setPadding(p, p, p, p);
        head.addView(Ui.text(this, "Truck Sales", 18, 0xFFFFFFFF, true));
        LinearLayout bar = Ui.box(this, false);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, Ui.dp(this, 6), 0, 0);
        TextView prev = Ui.chip(this, "◀", 0xFFFFFFFF, 0x33FFFFFF), next = Ui.chip(this, "▶", 0xFFFFFFFF, 0x33FFFFFF);
        TextView today = Ui.chip(this, "Today", Ui.PRIMARY, 0xFFFFFFFF);
        dateText = Ui.chip(this, "", 0xFFFFFFFF, 0x33FFFFFF);
        dateText.setTextSize(16);
        dateText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        prev.setOnClickListener(v -> changeDate(Calc.addDays(date, -1)));
        next.setOnClickListener(v -> changeDate(Calc.addDays(date, 1)));
        today.setOnClickListener(v -> changeDate(Calc.today()));
        dateText.setOnClickListener(v -> pickDate(date, this::changeDate));
        bar.addView(prev, Ui.gap(Ui.weight(0.6f), this));
        bar.addView(dateText, Ui.gap(Ui.weight(2.4f), this));
        bar.addView(next, Ui.gap(Ui.weight(0.6f), this));
        bar.addView(today, Ui.gap(Ui.weight(1f), this));
        head.addView(bar, Ui.fullWidth());
        root.addView(head, Ui.fullWidth());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Bottom tabs
        root.addView(Ui.divider(this));
        LinearLayout nav = Ui.box(this, false);
        nav.setBackgroundColor(Ui.CARD);
        nav.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        String[] names = {"Dashboard", "Entry", "Reports", "Settings"};
        for (int i = 0; i < 4; i++) {
            final int t = i;
            tabs[i] = Ui.chip(this, names[i], Ui.MUTED, Ui.CARD);
            tabs[i].setTextSize(13);
            tabs[i].setOnClickListener(v -> show(t));
            nav.addView(tabs[i], Ui.gap(Ui.weight(1), this));
        }
        root.addView(nav, Ui.fullWidth());
        setContentView(root);
        render();
    }

    interface DateCb { void on(String d); }

    void pickDate(String current, DateCb done) {
        Calendar c = Calc.cal(current);
        new DatePickerDialog(this, (v, y, m, d) -> done.on(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    void changeDate(String d) {
        confirmLeave(() -> { date = d; render(); });
    }

    /** Asks before discarding unsaved stock entries. */
    void confirmLeave(Runnable go) {
        if (screen != null && screen.dirty()) {
            new AlertDialog.Builder(this).setTitle("Unsaved entries")
                    .setMessage("You have not saved this truck's entries. Discard them?")
                    .setPositiveButton("Discard", (d, w) -> go.run())
                    .setNegativeButton("Stay", null).show();
        } else go.run();
    }

    private void show(int t) {
        confirmLeave(() -> {
            if (t == 3 && !unlocked) {
                askPin(() -> { unlocked = true; tab = 3; render(); });
                return;
            }
            tab = t;
            render();
        });
    }

    private void askPin(Runnable ok) {
        EditText pin = new EditText(this);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setHint("PIN");
        new AlertDialog.Builder(this).setTitle("Enter PIN to change rates / settings").setView(pin)
                .setPositiveButton("OK", (d, w) -> {
                    if (pin.getText().toString().equals(Db.get(this).setting("pin", "1234"))) ok.run();
                    else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Rebuilds the current tab (called after any change). */
    void render() {
        dateText.setText(Calc.nice(date));
        for (int i = 0; i < 4; i++) {
            tabs[i].setBackground(Ui.round(this, i == tab ? 0xFFE3EDFB : Ui.CARD));
            tabs[i].setTextColor(i == tab ? Ui.PRIMARY : Ui.MUTED);
            tabs[i].setTypeface(i == tab ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        }
        switch (tab) {
            case 1: screen = new EntryScreen(this); break;
            case 2: screen = new ReportScreen(this); break;
            case 3: screen = new SettingsScreen(this); break;
            default: screen = new DashboardScreen(this);
        }
        content.removeAllViews();
        View v = screen.build();
        content.addView(v);
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------- files: the phone's own "Save to" / "Open from" picker (Drive, Downloads, SD card...) ----------

    interface Out { void write(OutputStream o) throws Exception; }

    interface In { void read(InputStream i) throws Exception; }

    private Out pendingOut;
    private In pendingIn;
    private String pendingDone;

    /** Lets the user choose where to save a new file, then writes it. */
    void saveFile(String fileName, String mime, String doneMsg, Out w) {
        pendingOut = w;
        pendingDone = doneMsg;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, fileName);
        start(i, 1);
    }

    /** Lets the user pick a file, then reads it. */
    void openFile(String doneMsg, In r) {
        pendingIn = r;
        pendingDone = doneMsg;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        start(i, 2);
    }

    private void start(Intent i, int code) {
        try {
            startActivityForResult(i, code);
        } catch (ActivityNotFoundException e) {
            toast("No file app found on this phone");
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (code == 1 && pendingOut != null) {
                try (OutputStream o = getContentResolver().openOutputStream(uri, "wt")) { pendingOut.write(o); }
            } else if (code == 2 && pendingIn != null) {
                try (InputStream in = getContentResolver().openInputStream(uri)) { pendingIn.read(in); }
            } else return;
            new AlertDialog.Builder(this).setMessage(pendingDone).setPositiveButton("OK", null).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Could not complete")
                    .setMessage(e.getMessage() == null ? e.toString() : e.getMessage())
                    .setPositiveButton("OK", null).show();
        } finally {
            pendingOut = null;
            pendingIn = null;
            render();
        }
    }
}
