package com.example.meterview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private const val TIMER_VIBRATION = 5L
        private const val NUM_LEVEL = 5
        private const val SWIPE_MINIMUM_DISTANCE = 10
    }

    private var mPreviousY = 0f
    private var mScreenHeight: Int = 0
    private var mScreenWidth: Int = 0

    private var vibrator: Vibrator =
        getContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    //draw用
    private var mSwipeState = SwipeState.NONE
    private var mLevelButtonHeight = 50f
    private var mLevelButtonMargin = 35f
    private var connerRadius = 60
    private var mPaintStroke = 5f
    private var mLevelButtonDiffRatio = 0.5f
    private var mLevelButtonDifference = 35f

    private val mMaxMeterLevel: Int
        get() {
            return NUM_LEVEL - 1
        }

    private var mCurrentLevel: Int = 0

    private val mExtractLevels = mutableListOf<ExtractLevel>()

    internal enum class SwipeState {
        UP,
        DOWN,
        NONE
    }

    data class ExtractLevel(
        var levelIndex: Int = 0,
        var position: RectF
    )

    @SuppressLint("DrawAllocation")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        //スクリーンサイズを取得する。
        val displayMetrics = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(
            displayMetrics
        )
        mScreenWidth = displayMetrics.widthPixels
        mScreenHeight = displayMetrics.heightPixels

        //サイズの指定が会った際に大きさを取得する。
        if (MeasureSpec.UNSPECIFIED != MeasureSpec.getMode(widthMeasureSpec)) {
            mScreenWidth = MeasureSpec.getSize(widthMeasureSpec)
        }
        if (MeasureSpec.UNSPECIFIED != MeasureSpec.getMode(heightMeasureSpec)) {
            mScreenHeight = MeasureSpec.getSize(heightMeasureSpec)
        }
        //自分自身のサイズを setMeasuredDimension で確定させる
        setMeasuredDimension(mScreenWidth, mScreenHeight)
        initButtonSize(mScreenWidth, mScreenHeight)
    }

    private fun initButtonSize(mScreenWidth: Int, mScreenHeight: Int) {
        val maxBtnWidth = mScreenWidth.toFloat()
        val minBtnWidth = mScreenWidth * mLevelButtonDiffRatio
        mLevelButtonDifference = (maxBtnWidth - minBtnWidth) / (Companion.NUM_LEVEL - 2)
        mLevelButtonMargin =
            (mScreenHeight - 2 * mPaintStroke) / (Companion.NUM_LEVEL * 2 + Companion.NUM_LEVEL - 1)
        mLevelButtonHeight = mLevelButtonMargin * 2
        mPaintStroke = mLevelButtonHeight / 9

        mExtractLevels.clear()
        for (i in 0 until Companion.NUM_LEVEL) {
            val width = when (i) {
                0 -> mScreenWidth.toFloat() - 2 * mPaintStroke
                else -> mExtractLevels[i - 1].position.width() - mLevelButtonDifference
            }

            val left = (mScreenWidth - width) / 2
            val top = i * (mLevelButtonHeight + mLevelButtonMargin) + mPaintStroke
            val rect = RectF(left, top, left + width, top + mLevelButtonHeight)

            //level indexは1~
            mExtractLevels.add(ExtractLevel(i, rect))
        }
        mExtractLevels.reverse()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawLevels(canvas)
    }

    private fun drawLevels(canvas: Canvas) {
        mExtractLevels.forEach {
            val paint = Paint()
            paint.style = Paint.Style.FILL
            paint.color = ContextCompat.getColor(
                context,
                if (isLevelSelected(it.levelIndex)) R.color.colorLevel else R.color.emptyColorLevel
            )
            canvas.drawRoundRect(
                it.position,
                connerRadius.toFloat(),
                connerRadius.toFloat(),
                paint
            )
        }
    }

    private fun isLevelSelected(levelIndex: Int): Boolean {
        return mCurrentLevel <= levelIndex
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return true
        val x = event.x
        val y = event.y

        if (!isInsideTouchArea(x, y)) return true
        val currentTouchId = getTouchedLevelIndex(x, y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = y - mPreviousY
                if (abs(dy) > Companion.SWIPE_MINIMUM_DISTANCE) {
                    if (dy > 0) {
                        mSwipeState = SwipeState.DOWN
                    } else if (dy < 0) {
                        mSwipeState = SwipeState.UP
                    }
                }

                if (mCurrentLevel != currentTouchId && currentTouchId != -1) {
                    updateLevelState(currentTouchId)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (mSwipeState == SwipeState.NONE) {
                    if (mCurrentLevel != currentTouchId && currentTouchId != -1) {
                        updateLevelState(currentTouchId)
                        invalidate()
                    }
                }
                mSwipeState = SwipeState.NONE
                mPreviousY = 0f
            }
        }
        mPreviousY = y
        return true

    }

    private fun updateLevelState(levelIndex: Int) {
        if (levelIndex > mMaxMeterLevel || levelIndex < 0) return
        changeLevelWithVibration(levelIndex)
    }

    private fun changeLevelWithVibration(levelIndex: Int) {
        vibrator.vibrate(VibrationEffect.createOneShot(Companion.TIMER_VIBRATION, -1))
        mCurrentLevel = levelIndex
    }

    private fun getTouchedLevelIndex(x: Float, y: Float): Int {
        for (extractLevel in mExtractLevels) {
            if (extractLevel.position.contains(x, y)) return extractLevel.levelIndex
        }
        return -1
    }

    private fun isInsideTouchArea(x: Float, y: Float): Boolean {
        val topRect = mExtractLevels[mExtractLevels.lastIndex].position
        val bottomRect = mExtractLevels[0].position

        if (y < topRect.top || y > bottomRect.bottom) return false
        if (x < topRect.left || x > topRect.right) return false
        return true
    }

}
