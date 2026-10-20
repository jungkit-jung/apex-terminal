package com.trading.terminal.core.engine

import com.trading.terminal.core.data.model.TradeSide
import com.trading.terminal.core.data.model.TradeTick

data class CvdPoint(
    val timestamp: Long,
    val price: Double,
    val cumulativeDelta: Double,
    val delta: Double
)

enum class DivergenceType {
    NONE,
    BULLISH_ABSORPTION, // Price Lower Low, CVD Higher Low
    BEARISH_ABSORPTION  // Price Higher High, CVD Lower High
}

class CVDEngine(private val maxPoints: Int = 1000) {
    private var cumulativeDelta = 0.0
    private val points = ArrayList<CvdPoint>()

    fun onTick(tick: TradeTick): CvdPoint {
        val delta = if (tick.side == TradeSide.BUY) tick.quantity else -tick.quantity
        cumulativeDelta += delta

        val point = CvdPoint(
            timestamp = tick.timestamp,
            price = tick.price,
            cumulativeDelta = cumulativeDelta,
            delta = delta
        )

        points.add(point)
        if (points.size > maxPoints) {
            points.removeAt(0)
        }
        return point
    }

    fun getPoints(): List<CvdPoint> = points

    fun checkDivergence(lookback: Int = 50): DivergenceType {
        if (points.size < lookback) return DivergenceType.NONE

        val recent = points.takeLast(lookback)
        val p1 = recent.first()
        val p2 = recent.last()

        val priceChange = p2.price - p1.price
        val cvdChange = p2.cumulativeDelta - p1.cumulativeDelta

        return when {
            priceChange < 0 && cvdChange > 0 -> DivergenceType.BULLISH_ABSORPTION
            priceChange > 0 && cvdChange < 0 -> DivergenceType.BEARISH_ABSORPTION
            else -> DivergenceType.NONE
        }
    }
}
