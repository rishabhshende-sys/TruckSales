package com.retail.trucksales;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

/** Small helpers for building the UI in code (keeps the app tiny, no layout libraries). */
final class Ui {
    private Ui() {}

    static final int PRIMARY = 0xFF1565C0, BG = 0xFFF3F5F8, CARD = 0xFFFFFFFF, TEXT = 0xFF1F2937,
            MUTED = 0xFF6B7280, LINE = 0xFFE5E7EB, GREEN = 0xFF2E7D32, RED = 0xFFC62828;

    private static final Locale IN = new Locale("en", "IN");
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(IN);
    private static final NumberFormat QTY = NumberFormat.getNumberInstance(IN);

    static {
        MONEY.setMinimumFractionDigits(2);
        MONEY.setMaximumFractionDigits(2);
        QTY.setMaximumFractionDigits(2);
    }

    static String money(double v) { return MONEY.format(v); }

    static String qty(double v) { return QTY.format(v); }

    /** Short number for chart labels: 1.2k, 3.4L, 1.1Cr. */
    static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e7) return trim(v / 1e7) + "Cr";
        if (a >= 1e5) return trim(v / 1e5) + "L";
        if (a >= 1e3) return trim(v / 1e3) + "k";
        return trim(v);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.1f", v);
    }

    /** Plain editable number text ("" for null, no trailing .0). */
    static String plain(Double d) {
        if (d == null) return "";
        if (d == Math.rint(d)) return String.valueOf(d.longValue());
        return String.valueOf(d);
    }

    /** Parses a number field; blank returns null; bad input throws NumberFormatException. */
    static Double parse(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return null;
        return Double.valueOf(s);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout box(Context c, boolean vertical) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        return l;
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    static LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout card(Context c, String title) {
        LinearLayout l = box(c, true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(c, 10));
        g.setStroke(1, LINE);
        l.setBackground(g);
        int p = dp(c, 12);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = fullWidth();
        lp.setMargins(0, 0, 0, dp(c, 10));
        l.setLayoutParams(lp);
        if (title != null) {
            TextView t = text(c, title, 16, TEXT, true);
            t.setPadding(0, 0, 0, dp(c, 8));
            l.addView(t);
        }
        return l;
    }

    static GradientDrawable round(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, 8));
        return g;
    }

    /** Large, easy-to-tap text button (used for the date bar and the bottom tabs). */
    static TextView chip(Context c, String label, int textColor, int bg) {
        TextView t = text(c, label, 15, textColor, false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine();
        t.setPadding(dp(c, 4), dp(c, 12), dp(c, 4), dp(c, 12));
        t.setBackground(round(c, bg));
        t.setClickable(true);
        return t;
    }

    static LinearLayout.LayoutParams gap(LinearLayout.LayoutParams lp, Context c) {
        lp.setMargins(dp(c, 3), 0, dp(c, 3), 0);
        return lp;
    }

    static EditText num(Context c) {
        EditText e = new EditText(c);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setSelectAllOnFocus(true);
        e.setSingleLine();
        e.setTextSize(15);
        e.setGravity(Gravity.CENTER);
        return e;
    }

    static EditText field(Context c, String value) {
        EditText e = new EditText(c);
        e.setSingleLine();
        e.setTextSize(15);
        e.setText(value);
        return e;
    }

    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setTextSize(15);
        if (primary) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(PRIMARY);
            g.setCornerRadius(dp(c, 8));
            b.setBackground(g);
            b.setTextColor(0xFFFFFFFF);
        }
        return b;
    }

    /** One table row: first column wide/left, the rest right-aligned numbers. */
    static LinearLayout row(Context c, boolean header, String... cells) {
        LinearLayout r = box(c, false);
        r.setPadding(0, dp(c, 6), 0, dp(c, 6));
        for (int i = 0; i < cells.length; i++) {
            TextView t = text(c, cells[i], header ? 13 : 14, header ? MUTED : TEXT, header);
            t.setGravity(i == 0 ? Gravity.START : Gravity.END);
            r.addView(t, weight(i == 0 ? 1.6f : 1.2f));
        }
        return r;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    static ScrollView scroll(Context c, View child) {
        ScrollView s = new ScrollView(c);
        s.addView(child);
        return s;
    }

    static LinearLayout page(Context c) {
        LinearLayout l = box(c, true);
        int p = dp(c, 10);
        l.setPadding(p, p, p, p);
        return l;
    }
}
