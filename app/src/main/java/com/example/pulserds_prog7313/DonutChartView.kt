package com.example.pulserds_prog7313

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class DonutChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Segment colors + values
    private val accountData = listOf(
        40000f to Color.parseColor("#4ADE80"), // Cheque - green
        8000f  to Color.parseColor("#60A5FA"), // Crypto - blue
        2000f  to Color.parseColor("#FBBF24")  // Invest - yellow
    )

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // ⚠️ Change this to your exact @color/backgroundOrange hex
        // This MUST match the card background behind the donut
        color = Color.parseColor("#FFF3E0")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        val size = minOf(width, height).toFloat()
        val strokeWidth = size * 0.22f   // ring thickness — tweak between 0.18–0.28
        val radius = (size / 2f) - (strokeWidth / 2f)
        val cx = width / 2f
        val cy = height / 2f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        segmentPaint.strokeWidth = strokeWidth

        val total = accountData.sumOf { it.first.toDouble() }.toFloat()
        var startAngle = -90f
        val gapDegrees = 2f  // gap between segments

        for ((value, color) in accountData) {
            val sweep = (value / total) * 360f
            segmentPaint.color = color
            canvas.drawArc(oval, startAngle + gapDegrees / 2f, sweep - gapDegrees, false, segmentPaint)
            startAngle += sweep
        }

        // Punch out the hole to make it a donut
        val holeRadius = radius - (strokeWidth / 2f) - 4f  // 4f = inner padding
        canvas.drawCircle(cx, cy, holeRadius, holePaint)
    }
}