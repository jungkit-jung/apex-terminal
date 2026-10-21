package com.trading.terminal.feature.chart

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.trading.terminal.core.data.model.TradeSide
import com.trading.terminal.core.data.model.TradeTick
import com.trading.terminal.core.ui.theme.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    val priceRange = max(maxPrice - minPrice, 1.0)

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
            val axisWidth = 140f  // Right side price axis width
            val chartWidth = width - axisWidth
            val timeAxisHeight = 35f
            val chartHeight = height - timeAxisHeight

            if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

            fun priceToY(price: Double): Float {
                val ratio = (price - minPrice) / priceRange
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
                drawRect(
                    color = TerminalBackground,
                    topLeft = Offset(0f, 0f),
                    size = Size(chartWidth, chartHeight)
                )
            }

            // ── 2. PRICE GRID LINES (8 levels) ────────────────────────────
            val gridCount = 8
            val gridStep = priceRange / gridCount
            for (i in 0..gridCount) {
                val p = minPrice + gridStep * i
                val y = priceToY(p)
                drawLine(
                    color = TerminalGrid.copy(alpha = 0.20f),
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 0.8f
                )
            }

            val now = System.currentTimeMillis()
            val windowMs = 180_000L // 3-minute window

            // ── 3. TRAILING REAL-TIME PRICE LINE (Bookmap Golden Path) ─────
            if (recentTicks.size >= 2) {
                var prevOffset: Offset? = null
                for (tick in recentTicks.takeLast(120)) {
                    val age = now - tick.timestamp
                    if (age > windowMs || tick.price < minPrice || tick.price > maxPrice) continue

                    val xNorm = 1.0f - (age.toFloat() / windowMs.toFloat())
                    val x = xNorm * chartWidth
                    val y = priceToY(tick.price)
                    val pt = Offset(x, y)

                    prevOffset?.let { prev ->
                        drawLine(
                            color = Color(0xFFFFD600).copy(alpha = 0.85f),
                            start = prev,
                            end = pt,
                            strokeWidth = 2.2f
                        )
                    }
                    prevOffset = pt
                }
            }

            // ── 4. TRADE BUBBLES ────────────────────────────────────────────
            for (tick in recentTicks.takeLast(200)) {
                val age = now - tick.timestamp
                if (age > windowMs || tick.price < minPrice || tick.price > maxPrice) continue

                val xNorm = 1.0f - (age.toFloat() / windowMs.toFloat())
                val x = xNorm * chartWidth
                val y = priceToY(tick.price)

                val radius = (sqrt(tick.quantity) * 3.5).toFloat().coerceIn(3.0f, 26f)
                val alpha = (1f - age.toFloat() / windowMs.toFloat()).coerceIn(0.35f, 0.95f)

                val color = if (tick.side == TradeSide.BUY) {
                    ElectricEmerald.copy(alpha = alpha * 0.85f)
                } else {
                    VibrantCrimson.copy(alpha = alpha * 0.85f)
                }

                drawCircle(color = color, radius = radius, center = Offset(x, y))

                // White core for large trades
                if (radius > 9f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = 2.5f,
                        center = Offset(x, y)
                    )
                }
            }

            // ── 5. CURRENT PRICE LINE ───────────────────────────────────────
            val curY = priceToY(currentPrice)
            if (curY in 0f..chartHeight) {
                // Dashed price line
                var dashX = 0f
                val dashLen = 8f
                val gapLen = 4f
                while (dashX < chartWidth) {
                    drawLine(
                        color = ElectricEmerald.copy(alpha = 0.85f),
                        start = Offset(dashX, curY),
                        end = Offset((dashX + dashLen).coerceAtMost(chartWidth), curY),
                        strokeWidth = 1.5f
                    )
                    dashX += dashLen + gapLen
                }

                drawCircle(
                    color = ElectricEmerald,
                    radius = 4f,
                    center = Offset(chartWidth - 6f, curY)
                )
            }

            // ── 6. RIGHT PRICE AXIS ─────────────────────────────────────────
            drawRect(
                color = TerminalSurface,
                topLeft = Offset(chartWidth, 0f),
                size = Size(axisWidth, chartHeight)
            )
            drawLine(
                color = TerminalBorder,
                start = Offset(chartWidth, 0f),
                end = Offset(chartWidth, chartHeight),
                strokeWidth = 1.5f
            )

            // Price labels on axis
            drawIntoCanvas { canvas ->
                val textPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#99AABB")
                    textSize = 21f
                    typeface = Typeface.MONOSPACE
                    textAlign = Paint.Align.LEFT
                    isAntiAlias = true
                }
                val highlightPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#00FF88")
                    textSize = 22f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                    isAntiAlias = true
                }

                for (i in 0..gridCount) {
                    val p = minPrice + gridStep * i
                    val y = priceToY(p)
                    if (y < 12f || y > chartHeight - 12f) continue

                    val label = if (p >= 1000) String.format("%,.1f", p) else String.format("%.4f", p)
                    val paint = if (abs(p - currentPrice) < gridStep * 0.4) highlightPaint else textPaint
                    canvas.nativeCanvas.drawText(
                        label,
                        chartWidth + 8f,
                        y + 7f,
                        paint
                    )
                }

                // Floating current price badge
                if (curY in 0f..chartHeight) {
                    val bgPaint = Paint().apply {
                        color = android.graphics.Color.parseColor("#00FF88")
                    }
                    val curLabel = if (currentPrice >= 1000)
                        String.format("%,.1f", currentPrice)
                    else
                        String.format("%.4f", currentPrice)

                    canvas.nativeCanvas.drawRoundRect(
                        chartWidth + 3f, curY - 14f,
                        width - 2f, curY + 11f,
                        6f, 6f,
                        bgPaint
                    )
                    val curLabelPaint = Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 21f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        textAlign = Paint.Align.LEFT
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawText(
                        curLabel,
                        chartWidth + 8f,
                        curY + 6f,
                        curLabelPaint
                    )
                }
            }

            // ── 7. TIME AXIS (bottom strip) ─────────────────────────────────
            drawRect(
                color = TerminalSurface.copy(alpha = 0.95f),
                topLeft = Offset(0f, chartHeight),
                size = Size(chartWidth, timeAxisHeight)
            )
            drawLine(
                color = TerminalBorder,
                start = Offset(0f, chartHeight),
                end = Offset(chartWidth, chartHeight),
                strokeWidth = 1f
            )

            drawIntoCanvas { canvas ->
                val timePaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#667788")
                    textSize = 19f
                    typeface = Typeface.MONOSPACE
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val timeLabels = listOf("-3m", "-2m", "-1m", "now")
                val padX = 28f
                timeLabels.forEachIndexed { i, label ->
                    val x = padX + (chartWidth - padX * 2) * (i.toFloat() / (timeLabels.size - 1))
                    canvas.nativeCanvas.drawText(label, x, chartHeight + 24f, timePaint)
                }
            }
        }
    }
}
