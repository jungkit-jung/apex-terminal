package com.trading.terminal.feature.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.trading.terminal.core.data.model.TradeSide
import com.trading.terminal.core.engine.FootprintBar
import com.trading.terminal.core.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun FootprintChartView(
    bars: List<FootprintBar>,
    minPrice: Double,
    maxPrice: Double,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
    ) {
        val width = size.width
        val height = size.height

        val axisWidth = 140f
        val timeAxisHeight = 45f
        val chartWidth = width - axisWidth
        val chartHeight = height - timeAxisHeight

        if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

        // Show last 16 candles so they fit cleanly on mobile screens
        val visibleBars = bars.takeLast(16)

        // Dynamic price scaling adapted to visible candles
        val effectiveMin: Double
        val effectiveMax: Double

        if (visibleBars.isNotEmpty()) {
            val barMin = visibleBars.minOf { it.low }
            val barMax = visibleBars.maxOf { it.high }
            val spread = max(barMax - barMin, barMin * 0.0005)
            val pad = spread * 0.12
            effectiveMin = barMin - pad
            effectiveMax = barMax + pad
        } else if (maxPrice > minPrice && minPrice > 0) {
            effectiveMin = minPrice
            effectiveMax = maxPrice
        } else {
            effectiveMin = 80000.0
            effectiveMax = 85000.0
        }

        val priceRange = max(effectiveMax - effectiveMin, 1.0)

        fun priceToY(price: Double): Float {
            val ratio = (price - effectiveMin) / priceRange
            return (chartHeight * (1.0 - ratio)).toFloat().coerceIn(-100f, chartHeight + 100f)
        }

        // ── 1. BACKGROUND GRID LINES (6 levels) ──────────────────────────
        val gridLevels = 6
        val gridStep = priceRange / gridLevels
        for (i in 0..gridLevels) {
            val p = effectiveMin + gridStep * i
            val y = priceToY(p)
            drawLine(
                color = TerminalGrid.copy(alpha = 0.20f),
                start = Offset(0f, y),
                end = Offset(chartWidth, y),
                strokeWidth = 1f
            )
        }

        // Empty state indicator
        if (visibleBars.isEmpty()) {
            drawIntoCanvas { canvas ->
                val p = Paint().apply {
                    color = android.graphics.Color.parseColor("#8899AA")
                    textSize = 28f
                    typeface = Typeface.MONOSPACE
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawText(
                    "Waiting for candle & tick data...",
                    chartWidth / 2f,
                    chartHeight / 2f,
                    p
                )
            }
        }

        // ── 2. CANDLESTICK & FOOTPRINT PROFILE RENDERING ───────────────────
        if (visibleBars.isNotEmpty()) {
            val barCount = visibleBars.size
            val barWidth = chartWidth / barCount
            val candleBodyWidth = (barWidth * 0.65f).coerceIn(10f, 65f)

            val textPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#DDEEFF")
                textSize = (barWidth * 0.18f).coerceIn(16f, 22f)
                typeface = Typeface.MONOSPACE
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val greenPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#00E676")
                textSize = (barWidth * 0.18f).coerceIn(16f, 22f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val redPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#FF1744")
                textSize = (barWidth * 0.18f).coerceIn(16f, 22f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val deltaPaint = Paint().apply {
                textSize = 18f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            for (i in visibleBars.indices) {
                val bar = visibleBars[i]
                val xStart = i * barWidth
                val xCenter = xStart + (barWidth / 2f)

                val yHigh = priceToY(bar.high)
                val yLow = priceToY(bar.low)
                val yOpen = priceToY(bar.open)
                val yClose = priceToY(bar.close)

                val isBullish = bar.close >= bar.open
                val candleColor = if (isBullish) ElectricEmerald else VibrantCrimson

                // A. High-Low Wick
                drawLine(
                    color = candleColor,
                    start = Offset(xCenter, yHigh),
                    end = Offset(xCenter, yLow),
                    strokeWidth = 2.5f
                )

                // B. Real Candlestick Body (Open to Close)
                val topBody = min(yOpen, yClose)
                val botBody = max(yOpen, yClose)
                val bodyHeight = max(botBody - topBody, 3f)

                // Candle body fill (subtle background)
                drawRect(
                    color = candleColor.copy(alpha = 0.35f),
                    topLeft = Offset(xCenter - (candleBodyWidth / 2f), topBody),
                    size = Size(candleBodyWidth, bodyHeight)
                )
                // Candle body border
                drawRect(
                    color = candleColor,
                    topLeft = Offset(xCenter - (candleBodyWidth / 2f), topBody),
                    size = Size(candleBodyWidth, bodyHeight),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )

                // C. Footprint Bid x Ask Clusters (if bar is wide enough)
                val poc = bar.pocPrice
                val imbalances = bar.getImbalanceLevels(3.0)

                if (barWidth >= 40f && bar.clusters.isNotEmpty()) {
                    val sortedClusters = bar.clusters.values.sortedByDescending { it.price }
                    for (cluster in sortedClusters) {
                        val yLevel = priceToY(cluster.price)
                        if (yLevel < -20f || yLevel > chartHeight + 20f) continue
                        val rowHeight = 22f

                        // Highlight POC (Point of Control)
                        if (cluster.price == poc) {
                            drawRect(
                                color = Color(0xFF2979FF).copy(alpha = 0.45f),
                                topLeft = Offset(xStart + 3f, yLevel - (rowHeight / 2f)),
                                size = Size(barWidth - 6f, rowHeight)
                            )
                        }

                        val isBuyImbalance = imbalances.any { it.first == cluster.price && it.second == TradeSide.BUY }
                        val isSellImbalance = imbalances.any { it.first == cluster.price && it.second == TradeSide.SELL }

                        val bidStr = formatCompactCluster(cluster.bidVolume)
                        val askStr = formatCompactCluster(cluster.askVolume)

                        val bPaint = if (isSellImbalance) redPaint else textPaint
                        val aPaint = if (isBuyImbalance) greenPaint else textPaint

                        drawContext.canvas.nativeCanvas.drawText(
                            bidStr,
                            xCenter - (candleBodyWidth * 0.28f),
                            yLevel + 6f,
                            bPaint
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            askStr,
                            xCenter + (candleBodyWidth * 0.28f),
                            yLevel + 6f,
                            aPaint
                        )
                    }
                }

                // D. Delta value indicator at candle bottom
                val deltaStr = String.format("%+,.0f", bar.totalDelta)
                deltaPaint.color = if (bar.totalDelta >= 0) android.graphics.Color.parseColor("#00E676") else android.graphics.Color.parseColor("#FF1744")
                drawContext.canvas.nativeCanvas.drawText(
                    deltaStr,
                    xCenter,
                    chartHeight - 6f,
                    deltaPaint
                )
            }
        }

        // ── 3. RIGHT PRICE AXIS ───────────────────────────────────────────
        // Axis background
        drawRect(
            color = TerminalSurface,
            topLeft = Offset(chartWidth, 0f),
            size = Size(axisWidth, chartHeight)
        )
        // Axis vertical divider line
        drawLine(
            color = TerminalBorder,
            start = Offset(chartWidth, 0f),
            end = Offset(chartWidth, chartHeight),
            strokeWidth = 1.5f
        )

        drawIntoCanvas { canvas ->
            val axisTextPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#99AABB")
                textSize = 21f
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.LEFT
                isAntiAlias = true
            }

            for (i in 0..gridLevels) {
                val p = effectiveMin + gridStep * i
                val y = priceToY(p)
                if (y < 12f || y > chartHeight - 12f) continue

                val label = if (p >= 1000) String.format("%,.1f", p) else String.format("%.4f", p)
                canvas.nativeCanvas.drawText(
                    label,
                    chartWidth + 8f,
                    y + 7f,
                    axisTextPaint
                )
            }

            // Floating current active price badge
            val latestBar = visibleBars.lastOrNull()
            val curPrice = latestBar?.close ?: ((effectiveMin + effectiveMax) / 2.0)
            val curY = priceToY(curPrice)

            if (curY in 0f..chartHeight) {
                val isBull = (latestBar?.close ?: 0.0) >= (latestBar?.open ?: 0.0)
                val badgeBgColor = if (isBull) android.graphics.Color.parseColor("#00E676") else android.graphics.Color.parseColor("#FF1744")

                val badgePaint = Paint().apply { color = badgeBgColor }
                val badgeTextPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 21f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                    isAntiAlias = true
                }

                val curLabel = if (curPrice >= 1000) String.format("%,.1f", curPrice) else String.format("%.4f", curPrice)

                // Background pill
                canvas.nativeCanvas.drawRoundRect(
                    chartWidth + 3f, curY - 14f,
                    width - 2f, curY + 11f,
                    6f, 6f,
                    badgePaint
                )
                // Text
                canvas.nativeCanvas.drawText(
                    curLabel,
                    chartWidth + 8f,
                    curY + 6f,
                    badgeTextPaint
                )

                // Dashed horizontal line to current candle
                drawLine(
                    color = (if (isBull) ElectricEmerald else VibrantCrimson).copy(alpha = 0.8f),
                    start = Offset(0f, curY),
                    end = Offset(chartWidth, curY),
                    strokeWidth = 1f
                )
            }
        }

        // ── 4. BOTTOM TIME AXIS ───────────────────────────────────────────
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

            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            val barCount = visibleBars.size
            if (barCount > 0) {
                val step = max(1, barCount / 4)
                for (i in 0 until barCount step step) {
                    val bar = visibleBars[i]
                    val x = (chartWidth / barCount) * (i + 0.5f)
                    val timeStr = timeFormat.format(Date(bar.openTime))
                    canvas.nativeCanvas.drawText(timeStr, x, chartHeight + 28f, timePaint)
                }
            }
        }
    }
}

private fun formatCompactCluster(v: Double): String = when {
    v >= 1000.0 -> String.format("%.0fK", v / 1000.0)
    v >= 10.0   -> String.format("%.1f", v)
    v >= 1.0    -> String.format("%.1f", v)
    v > 0.0     -> String.format("%.2f", v)
    else        -> "0"
}
