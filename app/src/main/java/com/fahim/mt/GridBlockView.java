package com.fahim.mt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

public class GridBlockView extends View {
    private Paint grayPaint;
    private Paint redPaint;
    private Paint yellowPaint;
    private Paint textPaint;
    private Paint borderPaint;
    private BlockData data;

    public GridBlockView(Context context) {
        super(context);
        init();
    }

    public GridBlockView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        grayPaint = new Paint();
        grayPaint.setColor(Color.LTGRAY);
        grayPaint.setStyle(Paint.Style.FILL);

        redPaint = new Paint();
        redPaint.setColor(Color.RED);
        redPaint.setStyle(Paint.Style.FILL);

        yellowPaint = new Paint();
        yellowPaint.setColor(Color.YELLOW);
        yellowPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        borderPaint = new Paint();
        borderPaint.setColor(Color.DKGRAY);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
    }

    public void setData(BlockData data) {
        this.data = data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null) return;

        int width = getWidth();
        int height = getHeight();

        // 1. Draw Background
        if (data.isActive) {
            canvas.drawRect(0, 0, width, height, grayPaint);
        } else {
            // Unused/Inactive block could be white or transparent
            canvas.drawRect(0, 0, width, height, borderPaint);
            return;
        }

        // 2. Draw Match Sections (Red for Unique, Yellow for Duplicate)
        float sectionHeight = (float) height / 20f;
        if (!data.matchIndices.isEmpty()) {
            for (int index : data.matchIndices) {
                float top = index * sectionHeight;
                float bottom = (index + 1) * sectionHeight;
                canvas.drawRect(0, top, width, bottom, redPaint);
            }
        }
        if (!data.duplicateMatchIndices.isEmpty()) {
            for (int index : data.duplicateMatchIndices) {
                float top = index * sectionHeight;
                float bottom = (index + 1) * sectionHeight;
                canvas.drawRect(0, top, width, bottom, yellowPaint);
            }
        }

        // 3. Draw Match Count (Black Number)
        if (data.matchCount > 0) {
            String text = String.valueOf(data.matchCount);
            float x = width / 2f;
            float y = height / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f);
            canvas.drawText(text, x, y, textPaint);
        }

        // 4. Draw Border
        canvas.drawRect(0, 0, width, height, borderPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Force square aspect ratio
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width);
    }
}
