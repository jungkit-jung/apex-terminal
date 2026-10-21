package com.trading.terminal.feature.chart

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.trading.terminal.core.data.model.TradeSide
import com.trading.terminal.core.data.model.TradeTick
import com.trading.terminal.core.ui.theme.*
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * Trade Bubble (Marker on the Heatmap chart)
 */
data class ChartBubble(
    val price: Double,
    val quantity: Double,
    val side: TradeSide,
    val timestamp: Long,
    val xNormalized: Float = 1.0f
)

@Composable
fun BookmapHeatmapView(
    heatmapPixels: IntArray?,
    heatmapCols: Int = 120,
    heatmapRows: Int = 200,
    recentTicks: List<TradeTick>,
    currentPrice: Double,
    minPrice: Double,
    maxPrice: Double,
    modifier: Modifier = Modifier
) {
    var scaleX by remember { mutableFloatStateOf(1.0f) }
    var scaleY by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reusable Bitmap for heatmap blit
    val heatmapBitmap = remember(heatmapCols, heatmapRows) {
        Bitmap.createBitmap(heatmapCols, heatmapRows, Bitmap.Config.ARGB_8888)
    }

    LaunchedEffect(heatmapPixels) {
        heatmapPixels?.let { pixels ->
            if (pixels.size == heatmapCols * heatmapRows) {
                heatmapBitmap.setPixels(pixels, 0, heatmapCols, 0, 0, heatmapCols, heatmapRows)
            }
        }
    }

    val hasData = heatmapPixels != null && heatmapPixels.isNotEmpty()
    val priceRange = maxPrice - minPrice
    val safePriceRange = if (priceRange <= 0) 1.0 else priceRange

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scaleX = (scaleX * zoom).coerceIn(0.5f, 5.0f)
                    scaleY = (scaleY * zoom).coerceIn(0.5f, 5.0f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val axisWidth = 55f  // Right side price axis width
            val chartWidth = width - axisWidth
            val timeAxisHeight = 20f
            val chartHeight = height - timeAxisHeight

            fun priceToY(price: Double): Float {
                if (safePriceRange <= 0) return chartHeight / 2f
                val ratio = (price - minPrice) / safePriceRange
                return (chartHeight * (1.0 - ratio)).toFloat()
            }

            // ── 1. HEATMAP BLIT ────────────────────────────────────────────
            if (hasData) {
                drawImage(
                    image = heatmapBitmap.asImageBitmap(),
                    srcOffset = IntOffset(0, 0),
                    srcSize = IntSize(heatmapCols, heatmapRows),
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(chartWidth.roundToInt(), chartHeight.roundToInt())
                )
            } else {
                // Placeholder: gradient scan lines (like waiting for data)
                drawRect(
                    color = TerminalBackground,
                    topLeft = Offset(0f, 0f),
                    size = Size(chartWidth, chartHeight)
                )
                // Draw subtle grid
                for (i in 0..20) {
                    val y = chartHeight / 20f * i
                    drawLine(
                        color = TerminalGrid.copy(alpha = 0.12f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f
                    )
                }
                for (i in 0..30) {
                    val x = chartWidth / 30f * i
                    drawLine(
                        color = TerminalGrid.copy(alpha = 0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x, chartHeight),
                        strokeWidth = 1f
                    )
                }
            }

            // ── 2. PRICE GRID LINES (10 levels) ────────────────────────────
            val gridCount = 10
            val gridStep = safePriceRange / gridCount
            if (gridStep > 0) {
                for (i in 0..gridCount) {
                    val p = minPrice + gridStep * i
                    val y = priceToY(p)
                    drawLine(
                        color = TerminalGrid.copy(alpha = 0.25f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 0.5f
                    )
                }
            }

            // ── 3. TRADE BUBBLES ────────────────────────────────────────────
            val now = System.currentTimeMillis()
            val windowMs = 180_000L // 3-minute window

            for (tick in recentTicks.takeLast(200)) {
                val age = now - tick.timestamp
                if (age > windowMs || tick.price < minPrice || tick.price > maxPrice) continue

                val xNorm = 1.0f - (age.toFloat() / windowMs.toFloat())
                val x = xNorm * chartWidth
                val y = priceToY(tick.price)

                val radius = (sqrt(tick.quantity) * 3.5).toFloat().coerceIn(2.5f, 28f)
                val alpha = (1f - age.toFloat() / windowMs.toFloat()).coerceIn(0.3f, 0.9f)

                val color = if (tick.side == TradeSide.BUY) {
                    ElectricEmerald.copy(alpha = alpha * 0.8f)
                } else {
                    VibrantCrimson.copy(alpha = alpha * 0.8f)
                }

                drawCircle(color = color, radius = radius, center = Offset(x, y))

                // White core for large trades
                if (radius > 10f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = 2f,
                        center = Offset(x, y)
                    )
                }
            }

            // ── 4. CURRENT PRICE LINE ───────────────────────────────────────
            val curY = priceToY(currentPrice)
            if (curY in 0f..chartHeight) {
                // Dashed price line effect (alternating segments)
                var dashX = 0f
                val dashLen = 8f
                val gapLen = 4f
                while (dashX < chartWidth) {
                    drawLine(
                        color = ElectricEmerald.copy(alpha = 0.9f),
                        start = Offset(dashX, curY),
                        end = Offset((dashX + dashLen).coerceAtMost(chartWidth), curY),
                        strokeWidth = 1.5f
                    )
                    dashX += dashLen + gapLen
                }

                // Price beacon dot
                drawCircle(
                    color = ElectricEmerald,
                    radius = 4f,
                    center = Offset(chartWidth - 8f, curY)
                )
            }

            // ── 5. RIGHT PRICE AXIS ─────────────────────────────────────────
            // Axis background
            drawRect(
                color = TerminalSurface.copy(alpha = 0.95f),
                topLeft = Offset(chartWidth, 0f),
                size = Size(axisWidth, chartHeight)
            )
            // Axis border line
            drawLine(
                color = TerminalBorder,
                start = Offset(chartWidth, 0f),
                end = Offset(chartWidth, chartHeight),
                strokeWidth = 1f
            )

            // Price labels on axis
            drawIntoCanvas { canvas ->
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#8899AA")
                    textSize = 22f
                    typeface = android.graphics.Typeface.MONOSPACE
                    textAlign = android.graphics.Paint.Align.LEFT
                    isAntiAlias = true
                }
                val highlightPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#00FF88")
                    textSize = 24f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.LEFT
                    isAntiAlias = true
                }

                for (i in 0..gridCount) {
                    val p = minPrice + gridStep * i
                    val y = priceToY(p)
                    if (y < 8f || y > chartHeight - 8f) continue

                    val label = if (p > 1000) String.format("%.0f", p) else String.format("%.4f", p)
                    val paint = if (abs(p - currentPrice) < gridStep * 0.5) highlightPaint else textPaint
                    canvas.nativeCanvas.drawText(
                        label,
                        chartWidth + 4f,
                        y + 8f,
                        paint
                    )
                }

                // Floating current price label
                if (curY in 0f..chartHeight) {
                    val bgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#00FF88")
                    }
                    val curLabel = if (currentPrice > 1000)
                        String.format("%.1f", currentPrice)
                    else
                        String.format("%.4f", currentPrice)

                    // Label background box
                    canvas.nativeCanvas.drawRect(
                        chartWidth + 1f, curY - 13f,
                        width, curY + 8f,
                        bgPaint
                    )
                    val curLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 21f
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        textAlign = android.graphics.Paint.Align.LEFT
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawText(
                        curLabel,
                        chartWidth + 3f,
                        curY + 6f,
                        curLabelPaint
                    )
                }
            }

            // ── 6. TIME AXIS (bottom strip) ─────────────────────────────────
            drawRect(
                color = TerminalSurface.copy(alpha = 0.9f),
                topLeft = Offset(0f, chartHeight),
                size = Size(chartWidth, timeAxisHeight)
            )
            drawIntoCanvas { canvas ->
                val timePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#556677")
                    textSize = 20f
                    typeface = android.graphics.Typeface.MONOSPACE
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                val timeLabels = listOf("-3m", "-2m", "-1m", "now")
                timeLabels.forEachIndexed { i, label ->
                    val x = chartWidth * (i.toFloat() / (timeLabels.size - 1))
                    canvas.nativeCanvas.drawText(label, x, chartHeight + timeAxisHeight - 4f, timePaint)
                }
            }
        }
    }
}
