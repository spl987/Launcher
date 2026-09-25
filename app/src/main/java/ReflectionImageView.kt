package com.example.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView.ScaleType
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class ReflectionImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        // 倒影最大不透明度：0f=完全透明，1f=完全不透明，0.5f=半透明
        private const val REFLECTION_ALPHA = 0.6f
    }

    // 倒影与原图之间的缝隙
    private val reflectionGap = 0f

    // 倒影高度占原图显示区域高度的比例
    private val reflectionRatio = 0.16f

    // 圆角半径（像素）
    private val cornerRadius = 16f

    // 倒影透明度：1f=完全显示，0f=完全消失
    private var reflectionAlpha = REFLECTION_ALPHA

    // 焦点变化时的倒影动画
    private var focusAnimator: ValueAnimator? = null
    private val focusAnimDuration = 200L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 原图区域的高度（含 padding），用于 onDraw 计算
    private var originalViewHeight = 0

    init {
        // 使用 xfermode 需要软件层
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        // 让 View 可以获得焦点
        isFocusable = true
    }

    // ---------- 焦点变化 ----------
    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        // 获得焦点 → 倒影淡出；失去焦点 → 淡入到固定透明度
        animateReflection(if (gainFocus) 0f else REFLECTION_ALPHA)
    }

    private fun animateReflection(targetAlpha: Float) {
        focusAnimator?.cancel()

        if (reflectionAlpha == targetAlpha) return

        if (focusAnimDuration <= 0L) {
            reflectionAlpha = targetAlpha
            invalidate()
            return
        }

        focusAnimator = ValueAnimator.ofFloat(reflectionAlpha, targetAlpha).apply {
            duration = focusAnimDuration
            addUpdateListener {
                reflectionAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ---------- 测量 ----------
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val totalHeight = measuredHeight
        val gap = reflectionGap.toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        if (heightMode == MeasureSpec.EXACTLY) {
            // 固定高度：总高度固定，倒影占用其中一部分
            val availableContent = totalHeight - paddingTop - paddingBottom - gap
            val originalContentHeight = (availableContent / (1 + reflectionRatio)).toInt()
            originalViewHeight = originalContentHeight + paddingTop + paddingBottom
        } else {
            // wrap_content / unspecified：在原图高度基础上增加倒影高度
            val originalHeight = totalHeight
            val contentHeight = originalHeight - paddingTop - paddingBottom
            val reflectionHeight = (contentHeight * reflectionRatio).toInt()
            val newTotalHeight = originalHeight + gap + reflectionHeight
            setMeasuredDimension(measuredWidth, newTotalHeight)
            originalViewHeight = originalHeight
        }
    }

    // ---------- 绘制 ----------
    override fun onDraw(canvas: Canvas) {
        val drawable = drawable ?: return
        val bitmap = drawableToBitmap(drawable)
        if (bitmap.width <= 0 || bitmap.height <= 0) return

        val originalContentHeight = originalViewHeight - paddingTop - paddingBottom
        if (originalContentHeight <= 0) return

        val availableWidth = width - paddingLeft - paddingRight
        if (availableWidth <= 0) return

        // 计算原图绘制区域（根据 ScaleType）
        val dstRect = calculateDstRect(
            bitmap.width,
            bitmap.height,
            availableWidth,
            originalContentHeight,
            scaleType ?: ScaleType.FIT_CENTER
        )
        dstRect.offset(paddingLeft.toFloat(), paddingTop.toFloat())

        // ---------- 1. 绘制原图（四个圆角） ----------
        canvas.save()
        val originalPath = Path().apply {
            addRoundRect(dstRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(originalPath)
        canvas.drawBitmap(bitmap, null, dstRect, null)
        canvas.restore()

        // ---------- 2. 绘制倒影 ----------
        // 倒影完全透明时跳过绘制，省性能
        if (reflectionAlpha <= 0.001f) return

        val reflectionHeight = originalContentHeight * reflectionRatio
        if (reflectionHeight <= 0) return

        val reflectionTop = paddingTop + originalContentHeight + reflectionGap
        val reflectionBottom = reflectionTop + reflectionHeight
        val reflectionRect = RectF(
            paddingLeft.toFloat(),
            reflectionTop,
            (width - paddingRight).toFloat(),
            reflectionBottom
        )

        canvas.save()
        canvas.clipRect(reflectionRect)

        val layerId = canvas.saveLayer(
            reflectionRect.left,
            reflectionRect.top,
            reflectionRect.right,
            reflectionRect.bottom,
            null
        )

        // 平移到底部 + 垂直翻转
        canvas.translate(reflectionRect.left, reflectionRect.bottom)
        canvas.scale(1f, -1f)

        // 翻转坐标系下：原图顶部在 y=reflectionHeight - dstHeight，底部在 y=reflectionHeight
        val dLeft = dstRect.left - reflectionRect.left
        val dRight = dstRect.right - reflectionRect.left
        val dstHeight = dstRect.height()
        val dTop = reflectionHeight - dstHeight
        val dBottom = reflectionHeight
        val reflectionDst = RectF(dLeft, dTop, dRight, dBottom)

        // 倒影裁剪路径：只在屏幕顶部两角圆角（翻转坐标系里是 reflectionDst 的底部两角）
        val reflectionPath = Path().apply {
            addRoundRect(
                reflectionDst,
                floatArrayOf(
                    0f, 0f,                        // 左上角（对应屏幕底部）
                    0f, 0f,                        // 右上角（对应屏幕底部）
                    cornerRadius, cornerRadius,    // 右下角（对应屏幕顶部）
                    cornerRadius, cornerRadius     // 左下角（对应屏幕顶部）
                ),
                Path.Direction.CW
            )
        }
        canvas.clipPath(reflectionPath)

        // 画原图（翻转坐标系里即垂直镜像）
        canvas.drawBitmap(bitmap, null, reflectionDst, null)

        // 渐变遮罩：靠近原图（屏幕顶部）不透明 → 远离原图（屏幕底部）透明
        val maskAlpha = (255 * reflectionAlpha).toInt().coerceIn(0, 255)
        val shader = LinearGradient(
            0f, 0f, 0f, reflectionHeight,
            intArrayOf(
                Color.argb(0, 0, 0, 0),               // 底部：完全透明
                Color.argb(maskAlpha, 0, 0, 0)        // 顶部：不透明度由 reflectionAlpha 决定
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(0f, 0f, reflectionRect.width(), reflectionHeight, paint)
        paint.xfermode = null
        paint.shader = null

        canvas.restoreToCount(layerId)
        canvas.restore()
    }

    // ---------- 辅助：计算 ScaleType 下的绘制矩形 ----------
    private fun calculateDstRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        availableWidth: Int,
        availableHeight: Int,
        scaleType: ScaleType
    ): RectF {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return RectF()

        val srcRatio = bitmapWidth.toFloat() / bitmapHeight
        val dstRatio = availableWidth.toFloat() / availableHeight

        var left = 0f
        var top = 0f
        var right = availableWidth.toFloat()
        var bottom = availableHeight.toFloat()

        when (scaleType) {
            ScaleType.FIT_XY -> {
                // 填满
            }
            ScaleType.FIT_CENTER -> {
                if (srcRatio > dstRatio) {
                    val height = availableWidth / srcRatio
                    top = (availableHeight - height) / 2f
                    bottom = top + height
                } else {
                    val width = availableHeight * srcRatio
                    left = (availableWidth - width) / 2f
                    right = left + width
                }
            }
            ScaleType.CENTER_CROP -> {
                if (srcRatio > dstRatio) {
                    val width = availableHeight * srcRatio
                    left = (availableWidth - width) / 2f
                    right = left + width
                } else {
                    val height = availableWidth / srcRatio
                    top = (availableHeight - height) / 2f
                    bottom = top + height
                }
            }
            ScaleType.CENTER -> {
                val width = bitmapWidth.toFloat()
                val height = bitmapHeight.toFloat()
                left = (availableWidth - width) / 2f
                top = (availableHeight - height) / 2f
                right = left + width
                bottom = top + height
            }
            ScaleType.CENTER_INSIDE -> {
                val scale = min(
                    1f,
                    min(
                        availableWidth.toFloat() / bitmapWidth,
                        availableHeight.toFloat() / bitmapHeight
                    )
                )
                val width = bitmapWidth * scale
                val height = bitmapHeight * scale
                left = (availableWidth - width) / 2f
                top = (availableHeight - height) / 2f
                right = left + width
                bottom = top + height
            }
            else -> {
                if (srcRatio > dstRatio) {
                    val height = availableWidth / srcRatio
                    top = (availableHeight - height) / 2f
                    bottom = top + height
                } else {
                    val width = availableHeight * srcRatio
                    left = (availableWidth - width) / 2f
                    right = left + width
                }
            }
        }
        return RectF(left, top, right, bottom)
    }

    // ---------- 辅助：Drawable -> Bitmap ----------
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        focusAnimator?.cancel()
        focusAnimator = null
    }
}