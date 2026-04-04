package com.example.pulserds_prog7313

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import com.example.pulserds_prog7313.R

/**
 * DonutChartView — a self-contained donut chart that renders
 * fully in the Android Studio XML Design preview (no library needed).
 *
 * XML attributes (all optional, defined in res/values/attrs.xml):
 *   app:donutExpensePercent   float  0–100   (default 23.2)
 *   app:donutExpenseColor     color          (default #2A1A1A)
 *   app:donutSavedColor       color          (default #2EC4B6)
 *   app:donutHoleColor        color          (default #FFBB55)
 *   app:donutCenterText       string         (default "76.8%\nSaved")
 *   app:donutCenterTextColor  color          (default #1A1A1A)
 *   app:donutCenterTextSize   float sp       (default 13)
 *   app:donutThickness        float 0–1      (default 0.38 = 38% of radius)
 *   app:donutGap              float degrees  (default 3)
 */
class DonutChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Configurable properties ───────────────────────────────
    var expensePercent: Float = 23.2f
        set(value) { field = value; invalidate() }

    var expenseColor: Int = Color.parseColor("#2A1A1A")
        set(value) { field = value; expensePaint.color = value; invalidate() }

    var savedColor: Int = Color.parseColor("#2EC4B6")
        set(value) { field = value; savedPaint.color = value; invalidate() }

    var holeColor: Int = Color.parseColor("#FFBB55")
        set(value) { field = value; holePaint.color = value; invalidate() }

    var centerText: String = "76.8%\nSaved"
        set(value) { field = value; invalidate() }

    var centerTextColor: Int = Color.parseColor("#1A1A1A")
        set(value) { field = value; textPaint.color = value; invalidate() }

    var centerTextSizeSp: Float = 13f
        set(value) { field = value; textPaint.textSize = spToPx(value); invalidate() }

    // thickness as fraction of radius (0.38 = 38% thick ring)
    var thickness: Float = 0.38f
        set(value) { field = value; invalidate() }

    // gap between slices in degrees
    var gapDegrees: Float = 3f
        set(value) { field = value; invalidate() }

    // ── Paints ────────────────────────────────────────────────
    private val expensePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2A1A1A")
    }
    private val savedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2EC4B6")
    }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFBB55")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.FILL
        color     = Color.parseColor("#1A1A1A")
        textAlign = Paint.Align.CENTER
        textSize  = spToPx(13f)
        isFakeBoldText = true
    }

    private val oval = RectF()

    // ── Read XML attrs ────────────────────────────────────────
    init {
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.DonutChartView) {
                expensePercent   = getFloat(R.styleable.DonutChartView_donutExpensePercent, 23.2f)
                expenseColor     = getColor(R.styleable.DonutChartView_donutExpenseColor,  Color.parseColor("#2A1A1A"))
                savedColor       = getColor(R.styleable.DonutChartView_donutSavedColor,    Color.parseColor("#2EC4B6"))
                holeColor        = getColor(R.styleable.DonutChartView_donutHoleColor,     Color.parseColor("#FFBB55"))
                centerText       = getString(R.styleable.DonutChartView_donutCenterText)  ?: "76.8%\nSaved"
                centerTextColor  = getColor(R.styleable.DonutChartView_donutCenterTextColor, Color.parseColor("#1A1A1A"))
                centerTextSizeSp = getFloat(R.styleable.DonutChartView_donutCenterTextSize, 13f)
                thickness        = getFloat(R.styleable.DonutChartView_donutThickness, 0.38f)
                gapDegrees       = getFloat(R.styleable.DonutChartView_donutGap, 3f)

                // Apply parsed colours to paints
                expensePaint.color = expenseColor
                savedPaint.color   = savedColor
                holePaint.color    = holeColor
                textPaint.color    = centerTextColor
                textPaint.textSize = spToPx(centerTextSizeSp)
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - 4f
        val innerRadius = radius * (1f - thickness)

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val savedPercent = 100f - expensePercent
        val gap = gapDegrees

        // Start at top (-90°), draw expense slice first
        val expenseSweep = (expensePercent / 100f) * 360f - gap
        val savedSweep   = (savedPercent   / 100f) * 360f - gap

        // ── Draw expense slice ─────────────────────────────────
        drawSlice(canvas, cx, cy, radius, innerRadius, -90f + gap / 2f, expenseSweep, expensePaint)

        // ── Draw saved slice ───────────────────────────────────
        drawSlice(canvas, cx, cy, radius, innerRadius, -90f + gap / 2f + expenseSweep + gap, savedSweep, savedPaint)

        // ── Draw hole (covers centre to create donut) ──────────
        canvas.drawCircle(cx, cy, innerRadius, holePaint)

        // ── Draw centre text ───────────────────────────────────
        val lines = centerText.split("\n")
        val lineHeight = textPaint.textSize * 1.2f
        val totalTextHeight = lineHeight * lines.size
        val startY = cy - totalTextHeight / 2f + textPaint.textSize * 0.85f

        lines.forEachIndexed { i, line ->
            canvas.drawText(line, cx, startY + i * lineHeight, textPaint)
        }
    }

    /**
     * Draws a single donut slice as a filled arc minus the inner circle.
     * We draw the outer arc then fill inward using a triangle fan approach
     * via Path for a proper filled ring segment.
     */
    private fun drawSlice(
        canvas: Canvas,
        cx: Float, cy: Float,
        outerR: Float, innerR: Float,
        startAngle: Float, sweepAngle: Float,
        paint: Paint
    ) {
        val path = android.graphics.Path()
        val outerOval = RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        val innerOval = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)

        path.arcTo(outerOval, startAngle, sweepAngle, false)
        path.arcTo(innerOval, startAngle + sweepAngle, -sweepAngle, false)
        path.close()

        canvas.drawPath(path, paint)
    }

    // ── Utility ───────────────────────────────────────────────
    private fun spToPx(sp: Float): Float =
        sp * resources.displayMetrics.scaledDensity
}