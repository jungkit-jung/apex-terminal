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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.trading.terminal.core.data.model.TradeSide
import com.trading.terminal.core.data.model.TradeTick
import com.trading.terminal.core.ui.theme.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Trade Bubble (Marker on the Heatmap chart)
 */
data class ChartBubble(
    val price: Double,
    val quantity: Double,
    val side: TradeSide,
    val timestamp: Long,
    val xNormalized: Float = 1.0f // 0.0 (left) to 1.0 (right / current time)
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

    // Reusable Android Bitmap for GPU blit
    val heatmapBitmap = remember(heatmapCols, heatmapRows) {
        Bitmap.createBitmap(heatmapCols, heatmapRows, Bitmap.Config.ARGB_8888)
    }

    // Update bitmap when pixel array is baked
    LaunchedEffect(heatmapPixels) {
        heatmapPixels?.let { pixels ->
            if (pixels.size == heatmapCols * heatmapRows) {
                heatmapBitmap.setPixels(pixels, 0, heatmapCols, 0, 0, heatmapCols, heatmapRows)
            }
        }
    }

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

            // 1. Draw Thermal Heatmap Background (Hardware Scaled Blit)
            drawImage(
                image = heatmapBitmap.asImageBitmap(),
                srcOffset = IntOffset(0, 0),
                srcSize = IntSize(heatmapCols, heatmapRows),
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(width.roundToInt(), height.roundToInt())
            )

            // Price conversion helper
            fun priceToY(price: Double): Float {
                if (maxPrice <= minPrice) return height / 2f
                val ratio = (price - minPrice) / (maxPrice - minPrice)
                return (height * (1.0 - ratio)).toFloat()
            }

            // 2. Draw Price Grid Lines
            val gridStep = (maxPrice - minPrice) / 10.0
            if (gridStep > 0) {
                var p = minPrice
                while (p <= maxPrice) {
                    val y = priceToY(p)
                    drawLine(
                        color = TerminalGrid.copy(alpha = 0.4f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    p += gridStep
                }
            }

            // 3. Draw Trade Bubbles (Taker Executions)
            // Radius proportional to sqrt(quantity) — typical Bookmap scaling
            val now = System.currentTimeMillis()
            val windowMs = 180_000L // 3 minutes historical window

            for (tick in recentTicks.takeLast(150)) {
                val age = now - tick.timestamp
                if (age > windowMs) continue

                val xNorm = 1.0f - (age.toFloat() / windowMs.toFloat())
                val x = xNorm * width
                val y = priceToY(tick.price)

                val radius = (sqrt(tick.quantity) * 4.0).toFloat().coerceIn(3f, 35f)
                val color = if (tick.side == TradeSide.BUY) {
                    ElectricEmerald.copy(alpha = 0.75f)
                } else {
                    VibrantCrimson.copy(alpha = 0.75f)
                }

                // Outer circle
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(x, y)
                )

                // White Core for large trades
                if (radius > 12f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = 2.5f,
                        center = Offset(x, y)
                    )
                }
            }

            // 4. Draw Current Price Line
            val curY = priceToY(currentPrice)
            drawLine(
                color = ElectricEmerald,
                start = Offset(0f, curY),
                end = Offset(width, curY),
                strokeWidth = 2f
            )

            // Price Dot / Beacon
            drawCircle(
                color = ElectricEmerald,
                radius = 5f,
                center = Offset(width - 15f, curY)
            )
        }
    }
}
