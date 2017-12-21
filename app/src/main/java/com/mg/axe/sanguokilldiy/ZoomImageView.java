package com.mg.axe.sanguokilldiy;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;

/**
 * Created by AxeChen on 2017/12/19.
 * OnGlobalLayoutListener：布局完全绘制完成之后会调用，可以用来获取View的真实高度。
 * ScaleGestureDetector：用来处理多点触控。
 */

public class ZoomImageView extends android.support.v7.widget.AppCompatImageView
        implements ViewTreeObserver.OnGlobalLayoutListener,
        ScaleGestureDetector.OnScaleGestureListener,
        View.OnTouchListener {

    /**
     * 初始化完毕
     */
    private boolean isInit = false;

    /**
     * 初始的缩放值
     */
    private float mInitScale;

    /**
     * 中等缩放值
     */
    private float mMidScale;

    /**
     * 最大的缩放值
     */
    private float mMaxScale;

    /**
     * 图片缩放平移使用的matrix
     */
    private Matrix mMatrix;

    /**
     * 捕获用户的多点触控的缩放比例
     */
    private ScaleGestureDetector mScaleGestureDetector;

    /**
     * 获取系统决定的最小移动距离
     */
    private int mTouchSlop;

    /**
     * 计算移动的距离是否可以移动
     */
    private boolean isCanMove;

    /**
     * 是否可以左右移动。当图片到达边缘时便无法左右滑动
     */
    private boolean isCheckLeftAndRight;

    /**
     * 是否可以左右移动。当图片到达边缘时便无法上下滑动
     */
    private boolean isCheckTopAndBottom;

    /**
     * 用于双击事件处理。
     */
    private GestureDetector mGestureDetector;

    /**
     * 是否正在处理双击事件
     */
    private boolean isDoubleClick = false;

    /**
     * 最后的多点的数量
     */
    private int mLastPointerCount;

    /**
     * 多点触控时的中心点坐标
     */
    private float mCenterX;

    /**
     * 多点触控时的中心点坐标
     */
    private float mCenterY;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mMatrix = new Matrix();
        setScaleType(ScaleType.MATRIX);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOnTouchListener(this);
        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDoubleTap(MotionEvent e) {

                if (isDoubleClick) {
                    return true;
                }
                float x = getX();
                float y = getY();
                if (getScale() < mMidScale) {
                    postDelayed(new AutoScaleRunnable(ZoomImageView.this, mMidScale, x, y), 16);
                    isDoubleClick = true;
                } else {
                    postDelayed(new AutoScaleRunnable(ZoomImageView.this, mInitScale, x, y), 16);
                    isDoubleClick = true;
                }
                setImageMatrix(mMatrix);
                return true;
            }
        });
    }

    /**
     * 平滑缩放处理，用属性动画应该会好很多
     */
    private static class AutoScaleRunnable implements Runnable {

        private WeakReference<ZoomImageView> zoomImageViewWeakReference;

        private float mTargetScale;
        private float x;
        private float y;

        private final float BIG = 1.07f;
        private final float SMALL = 0.9f;

        private float tmpScale;

        public AutoScaleRunnable(ZoomImageView zoomImageView, float mTargetScale, float x, float y) {
            this.mTargetScale = mTargetScale;
            this.x = x;
            this.y = y;
            this.zoomImageViewWeakReference = new WeakReference<>(zoomImageView);

            if (zoomImageViewWeakReference.get() == null) {
                return;
            }
            if (zoomImageViewWeakReference.get().getScale() < mTargetScale) {
                tmpScale = BIG;
            }
            if (zoomImageViewWeakReference.get().getScale() >= mTargetScale) {
                tmpScale = SMALL;
            }
        }

        @Override
        public void run() {
            if (zoomImageViewWeakReference.get() == null) {
                return;
            }
            ZoomImageView zoomImageView = zoomImageViewWeakReference.get();
            zoomImageView.mMatrix.postScale(tmpScale, tmpScale, x, y);
            zoomImageView.checkBorderWhenScale();
            zoomImageView.setImageMatrix(zoomImageView.mMatrix);

            float current = zoomImageView.getScale();
            if ((tmpScale > 1.0f && current < mTargetScale) || (tmpScale < 1.0f && current > mTargetScale)) {
                zoomImageView.postDelayed(this, 16);
            } else {
                float scale = mTargetScale / current;
                zoomImageView.mMatrix.postScale(scale, scale, x, y);
                zoomImageView.checkBorderWhenScale();
                zoomImageView.setImageMatrix(zoomImageView.mMatrix);
                zoomImageView.isDoubleClick = false;
            }
        }
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
     * 布局完成布局之后会调用此方法，计算出图片的初始缩放值，
     * 将图片平移缩放在布局中央。
     */
    @Override
    public void onGlobalLayout() {
        if (!isInit) {
            isInit = true;

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

            mMatrix.postTranslate(tranX, tranY);
            mMatrix.postScale(mInitScale, mInitScale, viewWidth / 2, viewHeight / 2);
            setImageMatrix(mMatrix);
        }
    }

    // 获取缩放值
    public float getScale() {
        float[] values = new float[9];
        mMatrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }


    @Override
    public boolean onScale(ScaleGestureDetector detector) {

        float scale = getScale();
        float scaleFactor = detector.getScaleFactor();

        if (getDrawable() == null) {
            return true;
        }

        if ((scale < mMaxScale && scaleFactor > 1.0f)
                || (scale > mInitScale && scaleFactor < 1.0f)) {

            if (scale * scaleFactor < mInitScale) {
                scaleFactor = mInitScale / scale;
            }
            if (scale * scaleFactor > mMaxScale) {
                scale = mMaxScale / scale;
            }
            mMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            checkBorderWhenScale();
            setImageMatrix(mMatrix);
        }
        return true;
    }

    /**
     * 获得图片的宽高，各点坐标
     *
     * @return
     */
    private RectF getMatrixRectF() {
        Matrix getMatrix = mMatrix;
        RectF rectF = new RectF();

        Drawable drawable = getDrawable();
        if (drawable != null) {
            rectF.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            getMatrix.mapRect(rectF);
        }
        return rectF;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        float x = 0;
        float y = 0;
        // 先双击判断
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        mScaleGestureDetector.onTouchEvent(event);

        // 计算出多点触控时，所有点的中心点位置
        int pointCount = event.getPointerCount();
        for (int i = 0; i < pointCount; i++) {
            x += event.getX(i);
            y += event.getY(i);
        }
        x /= pointCount;
        y /= pointCount;

        if (mLastPointerCount != pointCount) {
            isCanMove = false;
            mCenterX = x;
            mCenterY = y;
        }
        mLastPointerCount = pointCount;

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float dx = x - mCenterX;
                float dy = y - mCenterY;
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
                        mMatrix.postTranslate(dx, dy);
                        checkBorderWhenTranslate();
                        setImageMatrix(mMatrix);
                    }
                }
                mCenterX = x;
                mCenterY = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mLastPointerCount = 0;
                break;
        }

        return true;
    }


    /**
     * 滑动边界检测
     */
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
        mMatrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 放大缩小边界检测
     */
    private void checkBorderWhenScale() {
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

        mMatrix.postTranslate(deltaX, deltaY);
    }


    /**
     * 判断移动的距离是否算是移动
     *
     * @param x
     * @param y
     * @return
     */
    private boolean isMoveAction(float x, float y) {
        return Math.sqrt(x * x + y * y) > mTouchSlop;
    }
}
