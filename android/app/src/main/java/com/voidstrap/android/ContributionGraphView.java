package com.voidstrap.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** GitHub-style commit calendar: 52 week columns of 7 day cells, with month labels and Mon/Wed/Fri labels. */
public final class ContributionGraphView extends View {
    static final int WEEKS = 52;
    private static final int[] PALETTE = {0xFF161B22, 0xFF0E4429, 0xFF006D32, 0xFF26A641, 0xFF39D353};
    private static final String[] DAY_LABELS = {"", "Mon", "", "Wed", "", "Fri", ""};

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final float labelWidth;
    private final float topPad;
    private final float gap;
    private final float corner;

    private int[][] counts = new int[WEEKS][7];
    private String[] months = new String[WEEKS];
    private int max;

    public ContributionGraphView(Context context) {
        this(context, null);
    }

    public ContributionGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        labelWidth = 36 * density;
        topPad = 18 * density;
        gap = 3 * density;
        corner = 2 * density;
        textPaint.setColor(0xFF8B949E);
        textPaint.setTextSize(11 * density);
    }

    /** counts[week][day] with day 0 = Sunday, and a month label where a new month starts (otherwise null). */
    public void setData(int[][] weekCounts, String[] monthLabels) {
        counts = weekCounts;
        months = monthLabels;
        max = 0;
        for (int[] week : counts) for (int c : week) max = Math.max(max, c);
        requestLayout();
        invalidate();
    }

    private float pitch(int width) {
        return (width - labelWidth) / WEEKS;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        float pitch = pitch(width);
        setMeasuredDimension(width, Math.round(topPad + 7 * pitch));
    }

    private int colorFor(int count) {
        if (count <= 0 || max <= 0) return PALETTE[0];
        int level = (int) Math.ceil(4.0 * count / max);
        return PALETTE[Math.max(1, Math.min(4, level))];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pitch = pitch(getWidth());
        float size = pitch - gap;

        for (int w = 0; w < WEEKS; w++) {
            if (months[w] != null) {
                canvas.drawText(months[w], labelWidth + w * pitch, topPad - 6 * density, textPaint);
            }
            for (int d = 0; d < 7; d++) {
                float left = labelWidth + w * pitch;
                float top = topPad + d * pitch;
                rect.set(left, top, left + size, top + size);
                cellPaint.setColor(colorFor(counts[w][d]));
                canvas.drawRoundRect(rect, corner, corner, cellPaint);
            }
        }
        for (int d = 0; d < 7; d++) {
            if (DAY_LABELS[d].isEmpty()) continue;
            canvas.drawText(DAY_LABELS[d], 0, topPad + d * pitch + size * 0.85f, textPaint);
        }
    }
}
