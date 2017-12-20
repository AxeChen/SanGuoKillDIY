package com.mg.axe.sanguokilldiy;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;

/**
 * Created by AxeChen on 2017/12/19.
 */

public class ZoomImageView extends android.support.v7.widget.AppCompatImageView
        implements ViewTreeObserver.OnGlobalLayoutListener,
        ScaleGestureDetector.OnScaleGestureListener,
        View.OnTouchListener {

    private boolean init = false;

    private float mInitScale;

    private float mMidScale;

    private float mMaxScale;

    private Matrix matrix;

    /**
     * 捕获用户的多点触控的缩放比例
     */
    private ScaleGestureDetector mScaleGestureDetector;

    private int mTouchSlop;
    private boolean isCanMove;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private void init() {
        matrix = new Matrix();
        setScaleType(ScaleType.MATRIX);
        mScaleGestureDetector = new ScaleGestureDetector(getContext(), this);
        setOnTouchListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    /**
     * 布局完成布局之后会调用此方法
     */
    @Override
    public void onGlobalLayout() {
        if (!init) {
            init = true;

            // 获取控件的宽高
            int viewWidth = getWidth();
            int viewHeight = getHeight();

            // 获取图片的宽和高
            Drawable d = getDrawable();
            if (d == null) {
                return;
            }

            int dw = d.getIntrinsicWidth();
            int dh = d.getIntrinsicHeight();

            // 计算图片的对比和缩放的缩放值
            float scale = 1.0f;
            if (dw > viewWidth && dh < viewHeight) {
                scale = viewWidth * 1.0f / dw;
            }

            if (dh > viewHeight && dw < viewWidth) {
                scale = viewHeight * 1.0f / dh;
            }

            if ((dh > viewHeight && dw > viewWidth)
                    || (dw < viewWidth && dh < viewHeight)) {
                scale = Math.min(viewWidth * 1.0f / dw, viewHeight * 1.0f / dh);
            }

            mInitScale = scale;
            mMidScale = mInitScale * 2;
            mMaxScale = mInitScale * 4;

            // 将图片移动到控件中心
            int tranX = viewWidth / 2 - dw / 2;
            int tranY = viewHeight / 2 - dh / 2;

            matrix.postTranslate(tranX, tranY);
            matrix.postScale(mInitScale, mInitScale, viewWidth / 2, viewHeight / 2);
            setImageMatrix(matrix);
        }
    }

    // 获取缩放值
    public float getScale() {
        float[] values = new float[9];
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }


    @Override
    public boolean onScale(ScaleGestureDetector detector) {

        float scale = getScale();
        float scaleFactor = detector.getScaleFactor();

        if (getDrawable() == null) {
            return true;
        }

        // 控制缩放范围
        if ((scale < mMaxScale && scaleFactor > 1.0f)
                || (scale > mInitScale && scaleFactor < 1.0f)) {

            if (scale * scaleFactor < mInitScale) {
                scaleFactor = mInitScale / scale;
            }

            if (scale * scaleFactor > mMaxScale) {
                scale = mMaxScale / scale;
            }

            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());

            checkBorderAndCenter();
            setImageMatrix(matrix);
        }


        return true;
    }

    /**
     * 获得图片的宽高，各点坐标
     *
     * @return
     */
    private RectF getMatrixRectF() {
        Matrix getMatrix = matrix;
        RectF rectF = new RectF();

        Drawable drawable = getDrawable();
        if (drawable != null) {
            rectF.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            getMatrix.mapRect(rectF);
        }
        return rectF;
    }


    private void checkBorderAndCenter() {
        RectF rectF = getMatrixRectF();
        float deltaX = 0;
        float deltaY = 0;

        int width = getWidth();
        int height = getHeight();
        if (rectF.width() >= width) {
            if (rectF.left > 0) {
                deltaX = -rectF.left;
            }

            if (rectF.right < width) {
                deltaX = width - rectF.right;
            }
        }

        if (rectF.height() >= height) {
            if (rectF.top >= height) {
                deltaY = -rectF.top;
            }

            if (rectF.bottom < height) {
                deltaY = height - rectF.bottom;
            }
        }

        if (rectF.width() < width) {
            deltaX = width / 2 - rectF.right + rectF.width() / 2;
        }

        if (rectF.height() < height) {
            deltaY = height / 2 - rectF.bottom + rectF.height() / 2;
        }

        matrix.postTranslate(deltaX, deltaY);

    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    // 自由移动的
    // 自由移动的位置
    private int mLastPointerCount;

    private float mLastX;

    private float mLastY;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mScaleGestureDetector.onTouchEvent(event);

        float x = 0;
        float y = 0;

        int pointCount = event.getPointerCount();
        for (int i = 0; i < pointCount; i++) {
            x += event.getX(i);
            y += event.getY(i);
        }

        x /= pointCount;
        y /= pointCount;

        if (mLastPointerCount != pointCount) {
            isCanMove = false;
            mLastX = x;
            mLastY = y;
        }
        mLastPointerCount = pointCount;

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float dx = x - mLastX;
                float dy = y - mLastY;
                if (!isCanMove) {
                    isCanMove = isMoveAction(dx, dy);
                }
                if (isCanMove) {
                    RectF rectF = getMatrixRectF();
                    if (getDrawable() != null) {
                        isCheckLeftAndRight = isCheckTopAndBottom = true;
                        if (rectF.width() < getWidth()) {
                            isCheckLeftAndRight = false;
                            dx = 0;
                        }

                        if (rectF.height() < getHeight()) {
                            isCheckTopAndBottom = false;
                            dy = 0;
                        }

                        matrix.postTranslate(dx, dy);
                        checkBorderWhenTranslate();
                        setImageMatrix(matrix);
                    }
                }
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mLastPointerCount = 0;
                break;
        }

        return true;
    }

    private boolean isCheckLeftAndRight;
    private boolean isCheckTopAndBottom;

    private void checkBorderWhenTranslate() {
        RectF rectF = getMatrixRectF();
        float deltaX = 0;
        float deltaY = 0;

        int width = getWidth();
        int height = getHeight();

        if (rectF.top > 0 && isCheckTopAndBottom) {
            deltaY = -rectF.top;
        }

        if (rectF.bottom < height && isCheckTopAndBottom) {
            deltaY = height - rectF.bottom;
        }

        if (rectF.left > 0 && isCheckLeftAndRight) {
            deltaX = -rectF.left;
        }

        if (rectF.right < width && isCheckLeftAndRight) {
            deltaX = width - rectF.right;
        }
        matrix.postTranslate(deltaX, deltaY);
    }

    private boolean isMoveAction(float x, float y) {
        return Math.sqrt(x * x + y * y) > mTouchSlop;
    }
}
