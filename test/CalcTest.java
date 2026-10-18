package com.retail.trucksales;

/** Run: javac -d /tmp/out app/src/main/java/com/retail/trucksales/Calc.java test/CalcTest.java && java -cp /tmp/out com.retail.trucksales.CalcTest */
public class CalcTest {
    static int fails = 0;
    static void eq(String n, Object exp, Object got) {
        boolean ok = exp.equals(got);
        if (!ok) fails++;
        System.out.println((ok ? "PASS " : "FAIL ") + n + " expected=" + exp + " got=" + got);
    }
    public static void main(String[] a) {
        eq("sold basic", 50.0, Calc.sold(100, 50));
        eq("sold all", 100.0, Calc.sold(100, 0));
        eq("sold decimal", 12.75, Calc.sold(15.75, 3));
        eq("value", 2275.0, Calc.value(70, 32.5));
        eq("value rounding", 0.33, Calc.value(1, 0.333));
        eq("value decimal qty", 469.07, Calc.value(12.75, 36.79));
        eq("check ok", null == Calc.check(15, 15), true);
        eq("check closing>avail", "Closing stock is more than opening stock", Calc.check(15, 16));
        eq("check negative", "Stock cannot be negative", Calc.check(-1, 0));
        eq("niceStep 1234/4", 500.0, Calc.niceStep(1234 / 4.0));
        eq("niceStep 18", 20.0, Calc.niceStep(18));
        eq("addDays month end", "2026-11-01", Calc.addDays("2026-10-31", 1));
        eq("addDays year", "2025-12-31", Calc.addDays("2026-01-01", -1));
        eq("addMonths -11", "2025-11", Calc.addMonths("2026-10", -11));
        eq("daysInMonth Feb 2028", 29, Calc.daysInMonth("2028-02"));
        eq("daysInMonth Oct", 31, Calc.daysInMonth("2026-10"));
        eq("nice", "Fri, 09 Oct 2026", Calc.nice("2026-10-09"));
        eq("monthName", "Oct 2026", Calc.monthName("2026-10", true));
        System.out.println(fails == 0 ? "ALL CALC TESTS PASSED" : fails + " FAILED");
        System.exit(fails);
    }
}
