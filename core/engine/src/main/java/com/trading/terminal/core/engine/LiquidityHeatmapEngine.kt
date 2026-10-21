package com.trading.terminal.core.engine

import com.trading.terminal.core.data.model.PriceLevel
import kotlin.math.ln
import kotlin.math.roundToInt

class LiquidityHeatmapEngine(
    val timeColumns: Int = 120, // 120 time slices (e.g. last 2-5 minutes)
    val priceRows: Int = 200    // 200 vertical price levels
) {
    // 2D Matrix: matrix[row * timeColumns + col] = volume
    private val matrix = FloatArray(priceRows * timeColumns)
    private var currentColumn = 0
    var minPrice = 0.0
        private set
    var maxPrice = 0.0
        private set
    private var priceStep = 1.0

    // Reusable ARGB pixel buffer for Canvas/Bitmap blitting
    val pixelBuffer = IntArray(priceRows * timeColumns)

    fun updatePriceRange(centerPrice: Double, spanPercentage: Double = 0.015) {
        val span = centerPrice * spanPercentage
        minPrice = centerPrice - span
        maxPrice = centerPrice + span
        priceStep = (maxPrice - minPrice) / priceRows
        if (priceStep <= 0.0) priceStep = 1.0
    }

    /**
     * Record a snapshot of the current Order Book into the current time column.
     */
    fun recordDepthSnapshot(bids: List<PriceLevel>, asks: List<PriceLevel>) {
        if (minPrice <= 0.0) return

        val col = currentColumn

        // Clear current column
        for (r in 0 until priceRows) {
            matrix[r * timeColumns + col] = 0f
        }

        // Map Bids
        for (b in bids) {
            val r = priceToRow(b.price)
            if (r in 0 until priceRows) {
                matrix[r * timeColumns + col] += b.quantity.toFloat()
            }
        }

        // Map Asks
        for (a in asks) {
            val r = priceToRow(a.price)
            if (r in 0 until priceRows) {
                matrix[r * timeColumns + col] += a.quantity.toFloat()
            }
        }

        // Advance circular column pointer
        currentColumn = (currentColumn + 1) % timeColumns
    }

    private fun priceToRow(price: Double): Int {
        val row = ((price - minPrice) / priceStep).roundToInt()
        // Invert so high price is at row 0 (top of screen)
        return (priceRows - 1 - row).coerceIn(0, priceRows - 1)
    }

    /**
     * Render the 2D matrix into the 32-bit ARGB pixelBuffer.
     * Uses Logarithmic Normalization: ln(1 + V) / ln(1 + MaxV)
     */
    fun bakePixelBuffer(): IntArray {
        var maxVol = 1.0f
        for (v in matrix) {
            if (v > maxVol) maxVol = v
        }

        val logMax = ln(1.0f + maxVol)

        for (r in 0 until priceRows) {
            for (c in 0 until timeColumns) {
                val idx = r * timeColumns + c
                val vol = matrix[idx]

                if (vol <= 0.0001f) {
                    pixelBuffer[idx] = 0xFF08090C.toInt() // Dark Background
                    continue
                }

                val norm = (ln(1.0f + vol) / logMax).coerceIn(0f, 1f)
                pixelBuffer[idx] = mapThermalColor(norm)
            }
        }
        return pixelBuffer
    }

    /**
     * Bookmap Thermal Color Palette
     * 0.0 -> Deep Blue
     * 0.3 -> Aqua / Cyan
     * 0.6 -> Orange
     * 0.85 -> Yellow
     * 1.0 -> White
     */
    private fun mapThermalColor(intensity: Float): Int {
        val a = 0xFF
        val r: Int
        val g: Int
        val b: Int

        when {
            intensity < 0.25f -> {
                val t = intensity / 0.25f
                r = 0
                g = (t * 60).toInt()
                b = (80 + t * 140).toInt()
            }
            intensity < 0.50f -> {
                val t = (intensity - 0.25f) / 0.25f
                r = 0
                g = (60 + t * 195).toInt()
                b = 255
            }
            intensity < 0.75f -> {
                val t = (intensity - 0.50f) / 0.25f
                r = (t * 255).toInt()
                g = (255 - t * 65).toInt()
                b = (255 * (1f - t)).toInt()
            }
            else -> {
                val t = (intensity - 0.75f) / 0.25f
                r = 255
                g = (190 + t * 65).toInt()
                b = (t * 255).toInt()
            }
        }
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
