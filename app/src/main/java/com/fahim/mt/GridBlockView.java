package com.fahim.mt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.List;

public class GridBlockView extends View {
    private Paint grayPaint;
    private Paint redPaint;
    private Paint yellowPaint;
    private Paint bluePaint;
    private Paint goldPaint;
    private Paint textPaint;
    private Paint whiteTextPaint;
    private Paint borderPaint;
    private Paint blackPaint;
    private Paint heatMapPaint;
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

        bluePaint = new Paint();
        bluePaint.setColor(Color.BLUE);
        bluePaint.setStyle(Paint.Style.FILL);

        goldPaint = new Paint();
        goldPaint.setColor(Color.parseColor("#FFD700")); // Gold
        goldPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        whiteTextPaint = new Paint();
        whiteTextPaint.setColor(Color.WHITE);
        whiteTextPaint.setTextSize(40f);
        whiteTextPaint.setTextAlign(Paint.Align.CENTER);
        whiteTextPaint.setFakeBoldText(true);

        borderPaint = new Paint();
        borderPaint.setColor(Color.DKGRAY);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);

        blackPaint = new Paint();
        blackPaint.setColor(Color.BLACK);
        blackPaint.setStyle(Paint.Style.FILL);

        heatMapPaint = new Paint();
        heatMapPaint.setStyle(Paint.Style.FILL);
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
        if (!data.isActive) {
            canvas.drawRect(0, 0, width, height, borderPaint);
            return;
        }

        if (data.isBlankPageGroup) {
            canvas.drawRect(0, 0, width, height, blackPaint);
            if (data.blankPageCount > 1) {
                String text = String.valueOf(data.blankPageCount);
                drawCenteredText(canvas, text, width, height, whiteTextPaint);
            }
        } else if (data.matchCount > 0) {
            // Heat map background based on maxPrice (0.0 to 12.0)
            int bgColor = getHeatMapColor(data.maxPrice, data.totalPrice);
            heatMapPaint.setColor(bgColor);
            canvas.drawRect(0, 0, width, height, heatMapPaint);
        } else {
            canvas.drawRect(0, 0, width, height, grayPaint);
        }

        // 2. Draw Match Sections
        if (!data.isBlankPageGroup) {
            float sectionHeight = (float) height / 20f;
            drawSections(canvas, data.matchIndices, redPaint, sectionHeight, width);
            drawSections(canvas, data.duplicateMatchIndices, yellowPaint, sectionHeight, width);
            drawSections(canvas, data.historyMatchIndices, bluePaint, sectionHeight, width);
            drawSections(canvas, data.goldMatchIndices, goldPaint, sectionHeight, width);

            // 3. Draw Match Count (Black Number)
            if (data.matchCount > 0) {
                drawCenteredText(canvas, String.valueOf(data.matchCount), width, height, textPaint);
            }
        }

        // 4. Draw Border
        canvas.drawRect(0, 0, width, height, borderPaint);
    }

    private void drawSections(Canvas canvas, List<Integer> indices, Paint paint, float sectionHeight, int width) {
        if (indices != null && !indices.isEmpty()) {
            for (int index : indices) {
                float top = index * sectionHeight;
                float bottom = (index + 1) * sectionHeight;
                canvas.drawRect(0, top, width, bottom, paint);
            }
        }
    }

    private void drawCenteredText(Canvas canvas, String text, int width, int height, Paint paint) {
        float x = width / 2f;
        float y = height / 2f - ((paint.descent() + paint.ascent()) / 2f);
        canvas.drawText(text, x, y, paint);
    }

    private int getHeatMapColor(double maxPrice, double totalPrice) {
        double ratio = Math.min(maxPrice / 12.0, 1.0);
        // Base: Interpolate between LightYellow (#FFFFE0) and Gold (#FFD700)
        
        int r = 255;
        int g = (int) (255 - (255 - 215) * ratio);
        int b = (int) (224 - (224 - 0) * ratio);
        
        // Red Tint Overlay based on totalPrice (12.0 to 30.0)
        if (totalPrice > 12.0) {
            double redRatio = Math.min((totalPrice - 12.0) / (30.0 - 12.0), 1.0);
            // Blend with full red (255, 0, 0)
            g = (int) (g * (1.0 - redRatio));
            b = (int) (b * (1.0 - redRatio));
        }
        
        return Color.rgb(r, g, b);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Force square aspect ratio
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width);
    }
}
