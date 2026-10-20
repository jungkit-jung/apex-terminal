package com.trading.terminal.feature.chart

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.trading.terminal.core.data.model.TradeSide
import com.trading.terminal.core.engine.FootprintBar
import com.trading.terminal.core.ui.theme.*

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

        if (bars.isEmpty() || maxPrice <= minPrice) return@Canvas

        val barCount = bars.size.coerceAtMost(10) // display last 10 footprint bars
        val barWidth = (width / barCount).coerceAtLeast(60f)

        fun priceToY(price: Double): Float {
            val ratio = (price - minPrice) / (maxPrice - minPrice)
            return (height * (1.0 - ratio)).toFloat()
        }

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val greenPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#00E676")
            textSize = 22f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val redPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#FF1744")
            textSize = 22f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        for (i in bars.indices) {
            val bar = bars[i]
            val xStart = i * barWidth
            val xCenter = xStart + (barWidth / 2f)

            // 1. Draw High-Low Candle Wick
            val yHigh = priceToY(bar.high)
            val yLow = priceToY(bar.low)
            val yOpen = priceToY(bar.open)
            val yClose = priceToY(bar.close)

            val candleColor = if (bar.close >= bar.open) ElectricEmerald else VibrantCrimson
            drawLine(
                color = candleColor.copy(alpha = 0.5f),
                start = Offset(xCenter, yHigh),
                end = Offset(xCenter, yLow),
                strokeWidth = 2f
            )

            // 2. Draw Footprint Clusters (Bid x Ask numbers)
            val poc = bar.pocPrice
            val imbalances = bar.getImbalanceLevels(3.0)

            val sortedClusters = bar.clusters.values.sortedByDescending { it.price }
            for (cluster in sortedClusters) {
                val yLevel = priceToY(cluster.price)
                val rowHeight = 24f

                // Highlight POC
                if (cluster.price == poc) {
                    drawRect(
                        color = Color(0xFF2979FF).copy(alpha = 0.35f),
                        topLeft = Offset(xStart + 2f, yLevel - (rowHeight / 2f)),
                        size = Size(barWidth - 4f, rowHeight)
                    )
                }

                // Check Imbalance at this level
                val isBuyImbalance = imbalances.any { it.first == cluster.price && it.second == TradeSide.BUY }
                val isSellImbalance = imbalances.any { it.first == cluster.price && it.second == TradeSide.SELL }

                // Format numbers: Bid (Left) x Ask (Right)
                val bidStr = String.format("%.1f", cluster.bidVolume)
                val askStr = String.format("%.1f", cluster.askVolume)

                val bPaint = if (isSellImbalance) redPaint else textPaint
                val aPaint = if (isBuyImbalance) greenPaint else textPaint

                // Draw native text on Android Hardware Canvas
                drawContext.canvas.nativeCanvas.drawText(
                    bidStr,
                    xStart + (barWidth * 0.25f),
                    yLevel + 7f,
                    bPaint
                )

                // Divider line
                drawContext.canvas.nativeCanvas.drawText(
                    "x",
                    xCenter,
                    yLevel + 7f,
                    textPaint
                )

                drawContext.canvas.nativeCanvas.drawText(
                    askStr,
                    xStart + (barWidth * 0.75f),
                    yLevel + 7f,
                    aPaint
                )
            }
        }
    }
}
