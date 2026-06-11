package com.algo1127.batterychargeguess

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class BatteryWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        isDither = true // Smoother gradients
    }

    private var batteryLevel = 0f // 0.0 to 1.0
    private var previousBatteryLevel = 0f // For smooth transitions

    // Time variables for independent wave speeds
    private var timeBack = 0f
    private var timeMid = 0f
    private var timeFront = 0f

    // Corporate Palette
    private val bgColorTop = Color.parseColor("#F8FAFC")
    private val bgColorBottom = Color.parseColor("#E2E8F0")

    private var bgShader: Shader? = null
    private var frontShader: Shader? = null
    private var backShader: Shader? = null
    private var highlightShader: Shader? = null

    private var gridBitmap: Bitmap? = null

    fun setLevel(level: Int) {
        if (level < 0) return
        previousBatteryLevel = batteryLevel
        batteryLevel = level / 100f
        updateShaders()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            bgShader = LinearGradient(0f, 0f, 0f, h.toFloat(), bgColorTop, bgColorBottom, Shader.TileMode.CLAMP)

            gridBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawDotGrid(Canvas(gridBitmap!!))

            updateShaders()
        }
    }

    private fun updateShaders() {
        if (width == 0 || height == 0) return
        val surfaceY = height - (height * batteryLevel)
        val endY = height.toFloat()

        // ✨ SMOOTH COLOR INTERPOLATION ✨
        val (topColor, bottomColor) = getInterpolatedColor(batteryLevel)

        // Front wave gradient
        frontShader = LinearGradient(0f, surfaceY, 0f, endY, topColor, bottomColor, Shader.TileMode.CLAMP)

        // Back waves (semi-transparent)
        val alphaTop = Color.argb(90, Color.red(topColor), Color.green(topColor), Color.blue(topColor))
        val alphaBottom = Color.argb(90, Color.red(bottomColor), Color.green(bottomColor), Color.blue(bottomColor))
        backShader = LinearGradient(0f, surfaceY, 0f, endY, alphaTop, alphaBottom, Shader.TileMode.CLAMP)

        // ✨ SURFACE HIGHLIGHT SHADER ✨
        highlightShader = LinearGradient(
            0f, surfaceY, 0f, surfaceY + 40f,
            Color.argb(100, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
    }

    // ✨ SMOOTH COLOR TRANSITIONS ✨
    private fun getInterpolatedColor(level: Float): Pair<Int, Int> {
        return when {
            level > 0.6f -> {
                // Blue range - interpolate between sky blue and deep blue
                val factor = (level - 0.6f) / 0.4f // 0 to 1
                val topColor = lerpColor(
                    Color.parseColor("#60A5FA"), // Lighter blue
                    Color.parseColor("#3B82F6"), // Standard blue
                    1 - factor
                )
                val bottomColor = lerpColor(
                    Color.parseColor("#1D4ED8"),
                    Color.parseColor("#1E40AF"),
                    1 - factor
                )
                topColor to bottomColor
            }
            level > 0.3f -> {
                // Indigo range - interpolate between blue-indigo and purple-indigo
                val factor = (level - 0.3f) / 0.3f
                val topColor = lerpColor(
                    Color.parseColor("#818CF8"),
                    Color.parseColor("#A5B4FC"),
                    1 - factor
                )
                val bottomColor = lerpColor(
                    Color.parseColor("#4338CA"),
                    Color.parseColor("#6366F1"),
                    1 - factor
                )
                topColor to bottomColor
            }
            else -> {
                // Orange range - interpolate between soft and deep orange
                val factor = level / 0.3f
                val topColor = lerpColor(
                    Color.parseColor("#FDBA74"),
                    Color.parseColor("#FBBF24"),
                    factor
                )
                val bottomColor = lerpColor(
                    Color.parseColor("#C2410C"),
                    Color.parseColor("#EA580C"),
                    factor
                )
                topColor to bottomColor
            }
        }
    }

    private fun lerpColor(color1: Int, color2: Int, fraction: Float): Int {
        val r = (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * fraction).toInt()
        val g = (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * fraction).toInt()
        val b = (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * fraction).toInt()
        return Color.rgb(r, g, b)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Soft Corporate Gradient Background
        bgShader?.let {
            paint.shader = it
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        } ?: canvas.drawColor(bgColorTop)

        // ✨ PERFORMANCE: Only draw grid if battery > 0 ✨
        if (batteryLevel > 0f) {
            gridBitmap?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
        }

        if (batteryLevel <= 0f) {
            postInvalidateOnAnimation()
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val surfaceY = h - (h * batteryLevel)

        // 3. Animate Time for Waves
        timeBack += 0.02f
        timeMid += 0.035f
        timeFront += 0.05f

        // 4. Draw 3 Layered Waves (Back to Front)
        paint.shader = backShader

        // Back Wave: Large amplitude, slow, transparent
        drawWave(canvas, w, h, surfaceY, 28f, 0.008f, timeBack)

        // Mid Wave: Medium amplitude, normal speed
        drawWave(canvas, w, h, surfaceY, 18f, 0.012f, timeMid)

        // Front Wave: Small amplitude, fast, solid
        paint.shader = frontShader
        drawWave(canvas, w, h, surfaceY, 12f, 0.018f, timeFront)

        // ✨ SURFACE HIGHLIGHT ✨
        paint.shader = highlightShader
        paint.alpha = 120
        drawWaveHighlight(canvas, w, h, surfaceY, 12f, 0.018f, timeFront)
        paint.alpha = 255

        paint.shader = null

        // 5. Keep the 60fps animation loop going
        postInvalidateOnAnimation()
    }

    // ✨ NEW: Surface highlight for liquid effect ✨
    private fun drawWaveHighlight(canvas: Canvas, w: Float, h: Float, surfaceY: Float, amplitude: Float, frequency: Float, time: Float) {
        val path = Path()
        path.moveTo(0f, surfaceY)

        for (x in 0..w.toInt() step 4) {
            val y = surfaceY + (sin(x * frequency + time) * amplitude)
            path.lineTo(x.toFloat(), y)
        }

        path.lineTo(w, surfaceY)
        path.close()

        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }

    private fun drawDotGrid(targetCanvas: Canvas) {
        val dotColor = Color.parseColor("#94A3B8")
        paint.style = Paint.Style.FILL
        paint.color = dotColor
        val spacing = 30f
        val radius = 1.5f

        for (x in 0..targetCanvas.width step spacing.toInt()) {
            for (y in 0..targetCanvas.height step spacing.toInt()) {
                // ✨ FADE GRID NEAR TOP ✨
                val alpha = (255 * (1 - (y.toFloat() / targetCanvas.height * 0.5))).toInt()
                paint.alpha = alpha
                targetCanvas.drawCircle(x.toFloat(), y.toFloat(), radius, paint)
            }
        }
        paint.alpha = 255
    }

    private fun drawWave(canvas: Canvas, w: Float, h: Float, surfaceY: Float, amplitude: Float, frequency: Float, time: Float) {
        val path = Path()
        path.moveTo(0f, h)

        // ✨ SMOOTHER CURVES using quadratic Bezier ✨
        var x = 0f
        while (x <= w) {
            val y = surfaceY + (sin(x * frequency + time) * amplitude)
            val nextX = x + 8
            val nextY = surfaceY + (sin(nextX * frequency + time) * amplitude)
            path.quadTo(x, y, (x + nextX) / 2, (y + nextY) / 2)
            x = nextX
        }

        path.lineTo(w, h)
        path.close()

        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }
}