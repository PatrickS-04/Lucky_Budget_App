package com.example.pulserds_prog7313

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * SpendingGoalChartView
 * ---------------------
 * Custom Canvas view that renders a bar chart of daily spending
 * for the past month overlaid with a tinted min-max spending goal band.
 *
 * Visual language:
 *  ● Bars             – teal (#2EC4B6) when within the goal band,
 *                       amber (#FFBB55) when below the minimum,
 *                       red   (#F87171) when above the maximum.
 *  ● Goal band        – semi-transparent green fill between minGoal
 *                       and maxGoal lines.
 *  ● Min / Max lines  – dashed horizontal reference lines.
 *  ● X-axis labels    – abbreviated day numbers (every ~7th day).
 *
 * Usage:
 *   chart.setData(dailyTotals, minGoal, maxGoal)
 */
class SpendingGoalChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // -- Data ------------------------------------------------------------------

    private var dailyAmounts: List<Double> = emptyList()
    private var minGoal: Double = 0.0
    private var maxGoal: Double = 0.0

    /**
     * Push new data into the chart and trigger a redraw.
     *
     * @param dailyAmounts  Spending amounts per day (index 0 = day 1 of the month).
     *                      Values should be positive (absolute expense amounts).
     * @param minGoal       Lower bound of the acceptable spending band.
     * @param maxGoal       Upper bound of the acceptable spending band.
     */
    fun setData(dailyAmounts: List<Double>, minGoal: Double, maxGoal: Double) {
        this.dailyAmounts = dailyAmounts
        this.minGoal      = minGoal
        this.maxGoal      = maxGoal
        invalidate()
    }

    // -- Paints ----------------------------------------------------------------

    private val barPaintOk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2EC4B6")
        style = Paint.Style.FILL
    }
    private val barPaintLow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFBB55")
        style = Paint.Style.FILL
    }
    private val barPaintHigh = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F87171")
        style = Paint.Style.FILL
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2EC4B6")   // semi-transparent teal
        style = Paint.Style.FILL
    }
    private val minLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#2EC4B6")
        style       = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect  = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val maxLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#F87171")
        style       = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect  = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.parseColor("#4A3A20")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val goalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.parseColor("#4A3A20")
        textSize = 20f
        textAlign = Paint.Align.RIGHT
    }

    // -- Draw ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dailyAmounts.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        val w     = width.toFloat()
        val h     = height.toFloat()
        val padL  = 12f
        val padR  = 50f   // room for goal labels on the right
        val padT  = 20f
        val padB  = 32f   // room for x-axis day labels

        val chartW = w - padL - padR
        val chartH = h - padT - padB

        // Scale: compute the highest value across bars + goal lines so everything fits
        val dataMax  = dailyAmounts.maxOrNull() ?: 0.0
        val scaleMax = max(maxGoal * 1.15, dataMax * 1.15).let { if (it == 0.0) 1.0 else it }

        val n        = dailyAmounts.size
        val barGap   = 3f
        val barW     = (chartW / n) - barGap

        // -- Goal band fill ---------------------------------------------------
        if (maxGoal > 0) {
            val bandTop    = padT + chartH * (1.0 - minGoal / scaleMax).toFloat().coerceIn(0f, 1f)
            val bandBottom = padT + chartH * (1.0 - maxGoal / scaleMax).toFloat().coerceIn(0f, 1f)
            canvas.drawRect(padL, bandBottom, padL + chartW, bandTop, bandPaint)
        }

        // -- Bars -------------------------------------------------------------
        dailyAmounts.forEachIndexed { i, amount ->
            val barH   = (chartH * (amount / scaleMax)).toFloat().coerceAtLeast(0f)
            val left   = padL + i * (barW + barGap)
            val top    = padT + chartH - barH
            val right  = left + barW
            val bottom = padT + chartH

            val paint = when {
                maxGoal > 0 && amount > maxGoal -> barPaintHigh
                minGoal > 0 && amount < minGoal && amount > 0 -> barPaintLow
                else -> barPaintOk
            }

            val rx = barW * 0.25f   // rounded corners
            canvas.drawRoundRect(left, top, right, bottom, rx, rx, paint)

            // X-axis day label (every 7th day or first/last)
            if (i == 0 || (i + 1) % 7 == 0 || i == n - 1) {
                canvas.drawText("${i + 1}", left + barW / 2f, h - 6f, labelPaint)
            }
        }

        // -- Min / Max dashed lines + right-side labels -----------------------
        if (minGoal > 0) {
            val y = padT + chartH * (1.0 - minGoal / scaleMax).toFloat()
            canvas.drawLine(padL, y, padL + chartW, y, minLinePaint)
            canvas.drawText("Min", w - 6f, y + 6f, goalLabelPaint)
        }
        if (maxGoal > 0) {
            val y = padT + chartH * (1.0 - maxGoal / scaleMax).toFloat()
            canvas.drawLine(padL, y, padL + chartW, y, maxLinePaint)
            canvas.drawText("Max", w - 6f, y + 6f, goalLabelPaint)
        }
    }

    private fun drawEmptyState(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.parseColor("#7A6A50")
            textSize  = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("No spending data", width / 2f, height / 2f, paint)
    }
}