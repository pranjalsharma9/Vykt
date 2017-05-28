package com.nsit.pranjals.vykt.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.tzutalin.dlib.VisionDetRet;

import java.util.ArrayList;
import java.util.List;

import static com.nsit.pranjals.vykt.listeners.OnGetImageListener.INPUT_SIZE;

/**
 * Created by Pranjal on 28-05-2017.
 * View that draws feature points over the camera feed.
 */

public class FeatureView extends View {

    private Paint paint;
    private List<VisionDetRet> results;
    private Rect faceBounds;
    private float resizeRatio = 1.0f;
    private int translateX = 0;
    private int translateY = 0;
    private int mRatioWidth;
    private int mRatioHeight;

    public FeatureView(Context context) {
        super(context);
        init();
    }

    public FeatureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public FeatureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init () {
        faceBounds = new Rect();

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDraw (Canvas canvas) {

        if (results != null) {
            for (final VisionDetRet ret : results) {
                faceBounds.left = (int) (ret.getLeft() * resizeRatio + translateX);
                faceBounds.top = (int) (ret.getTop() * resizeRatio + translateY);
                faceBounds.right = (int) (ret.getRight() * resizeRatio + translateX);
                faceBounds.bottom = (int) (ret.getBottom() * resizeRatio + translateY);
                canvas.drawRect(faceBounds, paint);

                // Draw landmark
                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                for (Point point : landmarks) {
                    int pointX = (int) (point.x * resizeRatio) + translateX;
                    int pointY = (int) (point.y * resizeRatio) + translateY;
                    canvas.drawCircle(pointX, pointY, 2, paint);
                }
            }
        }
    }

    public void drawResults (List<VisionDetRet> results) {
        this.results = results;
        postInvalidate();
    }

    private void setTransformationConstants (int width, int height) {

        final float minDim = Math.min(width, height);

        // We only want the center square out of the original rectangle.
        final float translateX = Math.max(0, (width - minDim) / 2);
        final float translateY = Math.max(0, (height - minDim) / 2);

        final float scaleFactor = minDim / INPUT_SIZE;
        this.translateX = (int) translateX;
        this.translateY = (int) translateY;
        this.resizeRatio = scaleFactor;
    }

    public void setAspectRatio(final int width, final int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    // Makes the view fit the smaller dimension and expand as required in the other direction
    // to maintain the aspect ratio.
    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (height < ((float) width * mRatioHeight) / mRatioWidth) {
                int changedWidth = (height * mRatioWidth) / mRatioHeight;
                setMeasuredDimension((height * mRatioWidth) / mRatioHeight, height);
                setTransformationConstants(changedWidth, height);
            } else {
                int changedHeight = (width * mRatioHeight) / mRatioWidth;
                setMeasuredDimension(width, (width * mRatioHeight) / mRatioWidth);
                setTransformationConstants(width, changedHeight);
            }
        }
    }

}
