package de.drremote.trotecsl400.alert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import de.drremote.trotecsl400.incident.IncidentRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IncidentGraphRenderer {
    private const val Width = 1200
    private const val Height = 600
    private const val Padding = 70f

    fun render(
        context: Context,
        incidents: List<IncidentRecord>,
        label: String,
        hysteresisDb: Double? = null,
        primaryLabel: String = "Incident metric",
        secondaryLabel: String? = "LAeq 5 min",
        showSecondaryLine: Boolean = true
    ): RenderedGraph {
        val bitmap = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 40, 40)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 220, 220)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 40, 40)
            textSize = 28f
        }
        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            textSize = 22f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 112, 201)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val laeqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(46, 139, 87)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 112, 201)
            style = Paint.Style.FILL
        }
        val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 50, 47)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val thresholdBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(28, 220, 50, 47)
            style = Paint.Style.FILL
        }
        val hysteresisBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(22, 255, 140, 0)
            style = Paint.Style.FILL
        }

        val headerHeight = if (showSecondaryLine && secondaryLabel != null) 140f else 110f
        val plotLeft = Padding
        val plotTop = maxOf(Padding, headerHeight)
        val plotRight = Width - Padding
        val plotBottom = Height - Padding

        drawGrid(canvas, gridPaint, plotLeft, plotTop, plotRight, plotBottom)
        canvas.drawRect(plotLeft, plotTop, plotRight, plotBottom, axisPaint)

        canvas.drawText("SL400 Incidents", plotLeft, 40f, textPaint)
        canvas.drawText(label, plotLeft, 70f, subTextPaint)
        canvas.drawText("Primary: $primaryLabel", plotLeft, 98f, subTextPaint)
        if (showSecondaryLine && secondaryLabel != null) {
            canvas.drawText("Secondary: $secondaryLabel", plotLeft, 124f, subTextPaint)
        }

        val points = incidents
            .filter { it.metricValue != null }
            .sortedBy { it.timestampMs }
        if (points.isEmpty()) {
            canvas.drawText("No incidents", plotLeft, plotTop + 40f, subTextPaint)
            return saveBitmap(context, bitmap, label)
        }

        val minTime = points.first().timestampMs
        val maxTime = points.last().timestampMs
        val values = points.mapNotNull { it.metricValue }
        val laeqValues = points.mapNotNull { it.laEq5Min }
        val minValue = (values + laeqValues).minOrNull() ?: 0.0
        val maxValue = (values + laeqValues).maxOrNull() ?: 1.0
        val rawMin = minValue - 3.0
        val rawMax = maxValue + 3.0
        val range = (rawMax - rawMin).coerceAtLeast(1.0)
        val mid = (rawMin + rawMax) / 2.0
        val paddedMin = mid - range / 2.0
        val paddedMax = mid + range / 2.0

        val threshold = points.first().thresholdDb
        val thresholdY = mapY(threshold, paddedMin, paddedMax, plotTop, plotBottom)
        canvas.drawRect(plotLeft, plotTop, plotRight, thresholdY, thresholdBandPaint)
        val hysteresis = hysteresisDb?.takeIf { it > 0.0 }
        var hasHysteresisBand = false
        if (hysteresis != null) {
            val lower = threshold - hysteresis
            val lowerY = mapY(lower, paddedMin, paddedMax, plotTop, plotBottom)
            val topY = minOf(thresholdY, lowerY)
            val bottomY = maxOf(thresholdY, lowerY)
            canvas.drawRect(plotLeft, topY, plotRight, bottomY, hysteresisBandPaint)
            hasHysteresisBand = true
        }

        val path = Path()
        points.forEachIndexed { index, incident ->
            val x = mapX(incident.timestampMs, minTime, maxTime, plotLeft, plotRight)
            val y = mapY(incident.metricValue ?: minValue, paddedMin, paddedMax, plotTop, plotBottom)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            val pointColor = severityColor(
                incident.metricValue ?: minValue,
                threshold
            )
            pointPaint.color = pointColor
            canvas.drawCircle(x, y, 5f, pointPaint)
        }
        canvas.drawPath(path, linePaint)

        canvas.drawLine(plotLeft, thresholdY, plotRight, thresholdY, thresholdPaint)

        canvas.drawText(
            "Threshold ${String.format(Locale.US, "%.1f", threshold)} dB",
            plotRight - 280f,
            thresholdY - 8f,
            subTextPaint
        )

        if (showSecondaryLine && secondaryLabel != null) {
            drawLaeqLine(
                canvas,
                points,
                laeqPaint,
                minTime,
                maxTime,
                paddedMin,
                paddedMax,
                plotLeft,
                plotTop,
                plotRight,
                plotBottom
            )
        }
        drawYAxisLabels(canvas, subTextPaint, paddedMin, paddedMax, plotLeft, plotTop, plotBottom)
        drawXAxisLabels(canvas, subTextPaint, minTime, maxTime, plotLeft, plotBottom)
        drawLegend(canvas, subTextPaint, plotRight, hasHysteresisBand, primaryLabel, secondaryLabel, showSecondaryLine)

        return saveBitmap(context, bitmap, label)
    }

    private fun drawGrid(
        canvas: Canvas,
        paint: Paint,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        val rows = 4
        val cols = 6
        val height = bottom - top
        val width = right - left
        for (i in 1 until rows) {
            val y = top + height * (i / rows.toFloat())
            canvas.drawLine(left, y, right, y, paint)
        }
        for (i in 1 until cols) {
            val x = left + width * (i / cols.toFloat())
            canvas.drawLine(x, top, x, bottom, paint)
        }
    }

    private fun mapX(
        time: Long,
        min: Long,
        max: Long,
        left: Float,
        right: Float
    ): Float {
        if (max == min) return (left + right) / 2f
        val ratio = (time - min).toDouble() / (max - min).toDouble()
        return (left + (right - left) * ratio).toFloat()
    }

    private fun mapY(
        value: Double,
        min: Double,
        max: Double,
        top: Float,
        bottom: Float
    ): Float {
        if (max == min) return (top + bottom) / 2f
        val ratio = (value - min) / (max - min)
        return (bottom - (bottom - top) * ratio).toFloat()
    }

    private fun drawYAxisLabels(
        canvas: Canvas,
        paint: Paint,
        min: Double,
        max: Double,
        left: Float,
        top: Float,
        bottom: Float
    ) {
        val steps = 4
        for (i in 0..steps) {
            val value = min + (max - min) * (i / steps.toDouble())
            val y = mapY(value, min, max, top, bottom)
            val text = String.format(Locale.US, "%.1f", value)
            canvas.drawText(text, 10f, y + 8f, paint)
        }
    }

    private fun drawXAxisLabels(
        canvas: Canvas,
        paint: Paint,
        minTime: Long,
        maxTime: Long,
        left: Float,
        bottom: Float
    ) {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val steps = 3
        for (i in 0..steps) {
            val time = minTime + (maxTime - minTime) * i / steps
            val x = mapX(time, minTime, maxTime, left, Width - Padding)
            val text = formatter.format(Date(time))
            canvas.drawText(text, x - 30f, bottom + 30f, paint)
        }
    }

    private fun drawLaeqLine(
        canvas: Canvas,
        points: List<IncidentRecord>,
        paint: Paint,
        minTime: Long,
        maxTime: Long,
        minValue: Double,
        maxValue: Double,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        val laeqPoints = points
            .filter { it.laEq5Min != null }
            .sortedBy { it.timestampMs }
        if (laeqPoints.isEmpty()) return

        val path = Path()
        laeqPoints.forEachIndexed { index, incident ->
            val x = mapX(incident.timestampMs, minTime, maxTime, left, right)
            val y = mapY(incident.laEq5Min ?: minValue, minValue, maxValue, top, bottom)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val dashedPaint = Paint(paint).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f)
        }
        canvas.drawPath(path, dashedPaint)
    }

    private fun drawLegend(
        canvas: Canvas,
        paint: Paint,
        plotRight: Float,
        hasHysteresisBand: Boolean,
        primaryLabel: String,
        secondaryLabel: String?,
        showSecondaryLine: Boolean
    ) {
        val x = plotRight - 220f
        var y = 95f
        val labelPaint = Paint(paint).apply { textSize = 20f }

        val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 112, 201) }
        canvas.drawCircle(x, y, 6f, livePaint)
        canvas.drawText(primaryLabel, x + 12f, y + 6f, labelPaint)
        y += 24f

        if (showSecondaryLine && secondaryLabel != null) {
            val laeqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(46, 139, 87) }
            canvas.drawLine(x - 2f, y, x + 10f, y, laeqPaint)
            canvas.drawText(secondaryLabel, x + 12f, y + 6f, labelPaint)
            y += 24f
        }

        val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220, 50, 47) }
        canvas.drawLine(x - 2f, y, x + 10f, y, thresholdPaint)
        canvas.drawText("Threshold", x + 12f, y + 6f, labelPaint)
        if (hasHysteresisBand) {
            y += 24f
            val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 255, 140, 0)
                style = Paint.Style.FILL
            }
            canvas.drawRect(x - 2f, y - 8f, x + 10f, y + 4f, bandPaint)
            canvas.drawText("Hysteresis band", x + 12f, y + 6f, labelPaint)
        }
    }

    private fun severityColor(value: Double, threshold: Double): Int {
        val delta = value - threshold
        return when {
            delta >= 8.0 -> Color.rgb(220, 50, 47)
            delta >= 3.0 -> Color.rgb(255, 140, 0)
            else -> Color.rgb(0, 112, 201)
        }
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, label: String): RenderedGraph {
        val safeLabel = label.replace(Regex("[^a-zA-Z0-9_-]+"), "_").trim('_')
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "sl400_graph_${safeLabel}_$timestamp.png"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return RenderedGraph(file, Width, Height)
    }

    data class RenderedGraph(
        val file: File,
        val width: Int,
        val height: Int
    )
}
