package com.retail.trucksales;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

/** Lightweight bar chart drawn directly on canvas (no chart library needed). */
final class ChartView extends View {
    private String[] labels = new String[0];
    private double[] values = new double[0];
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();

    ChartView(Context c) {
        super(c);
        bar.setColor(Ui.PRIMARY);
        txt.setColor(Ui.MUTED);
        txt.setTextSize(Ui.dp(c, 10));
        grid.setColor(Ui.LINE);
        grid.setStrokeWidth(1);
        setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 190)));
    }

    void setData(String[] l, double[] v) {
        labels = l;
        values = v;
        invalidate();
    }

    private float dp(float v) { return Ui.dp(getContext(), v); }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float left = dp(38), right = dp(4), top = dp(16), bottom = dp(20);
        float ph = h - top - bottom, pw = w - left - right;
        double max = 0;
        for (double v : values) max = Math.max(max, v);
        if (max <= 0 || values.length == 0) {
            txt.setTextAlign(Paint.Align.CENTER);
            cv.drawText("No sales in this period", w / 2, h / 2, txt);
            cv.drawLine(left, top + ph, w - right, top + ph, grid);
            return;
        }
        double step = Calc.niceStep(max / 4);
        double axisMax = Math.ceil(max / step) * step;
        txt.setTextAlign(Paint.Align.RIGHT);
        for (double g = 0; g <= axisMax + step / 1000; g += step) {
            float y = top + ph * (float) (1 - g / axisMax);
            cv.drawLine(left, y, w - right, y, grid);
            cv.drawText(Ui.compact(g), left - dp(4), y + dp(3), txt);
        }
        int n = values.length;
        float slot = pw / n, bw = Math.max(1f, slot * 0.65f);
        int every = (int) Math.ceil(n / Math.max(1f, pw / dp(26)));
        boolean showValues = n <= 12;
        txt.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            float x = left + slot * i + (slot - bw) / 2, cx = x + bw / 2;
            float y = top + ph * (float) (1 - values[i] / axisMax);
            if (values[i] > 0) cv.drawRect(x, y, x + bw, top + ph, bar);
            if (i % every == 0) cv.drawText(labels[i], cx, h - dp(5), txt);
            if (showValues && values[i] > 0) cv.drawText(Ui.compact(values[i]), cx, y - dp(3), txt);
        }
    }
}
