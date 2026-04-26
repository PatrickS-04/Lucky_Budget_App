package com.example.pulserds_prog7313

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class DonutChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // No hardcoded defaults — starts empty until real data is pushed in
    private val _segments = mutableListOf<Pair<Float, Int>>()

    private var centerLine1: String = ""
    private var centerLine2: String = ""

    /** Replace all segments and redraw. */
    fun setSegments(segments: List<Pair<Float, Int>>) {
        _segments.clear()
        _segments.addAll(segments)
        invalidate()
    }

    /** Set the two lines of text shown in the donut hole. */
    fun setCenterLabel(line1: String, line2: String = "") {
        centerLine1 = line1
        centerLine2 = line2
        invalidate()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF9F1C")
    }

    private val emptyRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeCap   = Paint.Cap.BUTT
        color       = Color.parseColor("#1A3248")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style          = Paint.Style.FILL
        color          = Color.WHITE
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style          = Paint.Style.FILL
        color          = Color.parseColor("#FFEEDD")
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val size        = minOf(width, height).toFloat()
        val strokeWidth = size * 0.22f
        val radius      = (size / 2f) - (strokeWidth / 2f)
        val cx          = width / 2f
        val cy          = height / 2f
        val oval        = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        segmentPaint.strokeWidth   = strokeWidth
        emptyRingPaint.strokeWidth = strokeWidth

        if (_segments.isEmpty()) {
            // Draw a single grey placeholder ring when there's no data
            canvas.drawArc(oval, -90f, 360f, false, emptyRingPaint)
        } else {
            val total      = _segments.sumOf { it.first.toDouble() }.toFloat()
            var startAngle = -90f
            val gapDegrees = if (_segments.size == 1) 0f else 2f

            for ((value, color) in _segments) {
                val sweep = (value / total) * 360f
                segmentPaint.color = color
                canvas.drawArc(oval, startAngle + gapDegrees / 2f, sweep - gapDegrees, false, segmentPaint)
                startAngle += sweep
            }
        }

        // Punch the donut hole
        val holeRadius = radius - (strokeWidth / 2f) - 4f
        canvas.drawCircle(cx, cy, holeRadius, holePaint)

        // Centre text
        if (centerLine1.isNotEmpty()) {
            val holeDiameter  = holeRadius * 2f
            val mainTextSize  = holeDiameter * 0.18f
            val subTextSize   = holeDiameter * 0.11f

            labelPaint.textSize    = mainTextSize
            subLabelPaint.textSize = subTextSize

            val fm       = labelPaint.fontMetrics
            val lineH    = fm.descent - fm.ascent
            val subLineH = subLabelPaint.fontMetrics.let { it.descent - it.ascent }

            val hasSubtitle = centerLine2.isNotEmpty()
            val totalH      = if (hasSubtitle) lineH + subLineH * 1.1f else lineH

            val mainBaseline = cy - totalH / 2f - fm.ascent
            canvas.drawText(centerLine1, cx, mainBaseline, labelPaint)

            if (hasSubtitle) {
                val subBaseline = mainBaseline + lineH + subLineH * 0.1f - subLabelPaint.fontMetrics.ascent
                canvas.drawText(centerLine2, cx, subBaseline, subLabelPaint)
            }
        }
    }
}