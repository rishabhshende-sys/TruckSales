package com.retail.trucksales;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Pure calculation and date helpers (no Android dependencies, unit-tested on the JVM). */
final class Calc {
    private Calc() {}

    /** Quantity sold = Opening - Closing. */
    static double sold(double opening, double closing) {
        return round2(opening - closing);
    }

    /** Sales value = quantity sold x applicable rate. */
    static double value(double qty, double rate) {
        return round2(qty * rate);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Returns an error message, or null when the stock figures are valid. */
    static String check(double opening, double closing) {
        if (opening < 0 || closing < 0) return "Stock cannot be negative";
        if (closing > opening + 1e-9) return "Closing stock is more than opening stock";
        return null;
    }

    /** Round a chart axis step to 1, 2 or 5 x 10^n. */
    static double niceStep(double raw) {
        if (raw <= 0) return 1;
        double p = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / p;
        double n = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
        return n * p;
    }

    // ---- dates are stored as yyyy-MM-dd text so they sort and compare correctly ----

    static Calendar cal(String date) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(date.substring(0, 4)), Integer.parseInt(date.substring(5, 7)) - 1,
                Integer.parseInt(date.substring(8, 10)));
        return c;
    }

    static String fmt(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String today() {
        return fmt(Calendar.getInstance());
    }

    static String addDays(String date, int n) {
        Calendar c = cal(date);
        c.add(Calendar.DAY_OF_MONTH, n);
        return fmt(c);
    }

    /** month = yyyy-MM */
    static String addMonths(String month, int n) {
        Calendar c = cal(month + "-01");
        c.add(Calendar.MONTH, n);
        return fmt(c).substring(0, 7);
    }

    static int daysInMonth(String month) {
        return cal(month + "-01").getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    static String nice(String date) {
        return new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH).format(cal(date).getTime());
    }

    static String shortDate(String date) {
        return new SimpleDateFormat("dd MMM yy", Locale.ENGLISH).format(cal(date).getTime());
    }

    static String monthName(String month, boolean withYear) {
        return new SimpleDateFormat(withYear ? "MMM yyyy" : "MMM", Locale.ENGLISH)
                .format(cal(month + "-01").getTime());
    }
}
