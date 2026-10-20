package com.trading.terminal.core.engine

import com.trading.terminal.core.data.model.TradeSide
import com.trading.terminal.core.data.model.TradeTick
import java.util.concurrent.ConcurrentHashMap

/**
 * Single price tick cluster inside a Footprint Candle.
 * Stores Bid Volume (Taker Sell hitting Bid) and Ask Volume (Taker Buy lifting Ask).
 */
data class FootprintCluster(
    val price: Double,
    var bidVolume: Double = 0.0,
    var askVolume: Double = 0.0,
    var bidCount: Int = 0,
    var askCount: Int = 0
) {
    val totalVolume: Double get() = bidVolume + askVolume
    val delta: Double get() = askVolume - bidVolume
}

/**
 * Full Footprint Candle (Sierra Chart / Volman Style).
 */
data class FootprintBar(
    val openTime: Long,
    val closeTime: Long,
    var open: Double,
    var high: Double,
    var low: Double,
    var close: Double,
    var totalVolume: Double = 0.0,
    var totalDelta: Double = 0.0,
    var minDelta: Double = 0.0,
    var maxDelta: Double = 0.0,
    val clusters: ConcurrentHashMap<Double, FootprintCluster> = ConcurrentHashMap()
) {
    /**
     * Point of Control (POC) — level with greatest volume in candle.
     */
    val pocPrice: Double
        get() = clusters.values.maxByOrNull { it.totalVolume }?.price ?: close

    /**
     * Value Area (70% of total candle volume).
     */
    fun calculateValueArea(): Pair<Double, Double> {
        val sorted = clusters.values.sortedByDescending { it.totalVolume }
        val targetVol = totalVolume * 0.70
        var accumulated = 0.0
        val inVa = mutableListOf<Double>()

        for (c in sorted) {
            inVa.add(c.price)
            accumulated += c.totalVolume
            if (accumulated >= targetVol) break
        }

        val vah = inVa.maxOrNull() ?: high
        val valLow = inVa.minOrNull() ?: low
        return Pair(valLow, vah)
    }

    /**
     * Diagonal Bid/Ask Imbalance detection.
     * Ratio typically 3.0 (300%) or 4.0 (400%).
     */
    fun getImbalanceLevels(ratioThreshold: Double = 3.0): List<Pair<Double, TradeSide>> {
        val result = mutableListOf<Pair<Double, TradeSide>>()
        val sortedPrices = clusters.keys.sorted()

        for (i in 0 until sortedPrices.size - 1) {
            val pLower = sortedPrices[i]
            val pUpper = sortedPrices[i + 1]

            val lowerCluster = clusters[pLower] ?: continue
            val upperCluster = clusters[pUpper] ?: continue

            // Buying Imbalance: Ask volume at Upper >= Ratio * Bid volume at Lower
            if (lowerCluster.bidVolume > 0 && upperCluster.askVolume >= lowerCluster.bidVolume * ratioThreshold) {
                result.add(Pair(pUpper, TradeSide.BUY))
            }

            // Selling Imbalance: Bid volume at Lower >= Ratio * Ask volume at Upper
            if (upperCluster.askVolume > 0 && lowerCluster.bidVolume >= upperCluster.askVolume * ratioThreshold) {
                result.add(Pair(pLower, TradeSide.SELL))
            }
        }
        return result
    }
}

/**
 * Real-time Footprint Aggregator
 */
class FootprintEngine(
    val barDurationMs: Long = 60_000L, // 1 minute default
    val tickSize: Double = 0.50        // Symbol price precision bucket
) {
    private var currentBar: FootprintBar? = null
    private val completedBars = mutableListOf<FootprintBar>()

    fun onTick(tick: TradeTick): FootprintBar {
        val roundedPrice = roundToTick(tick.price)
        val barStartTime = (tick.timestamp / barDurationMs) * barDurationMs
        val barEndTime = barStartTime + barDurationMs

        var bar = currentBar
        if (bar == null || tick.timestamp >= bar.closeTime) {
            if (bar != null) {
                completedBars.add(bar)
                if (completedBars.size > 500) completedBars.removeAt(0)
            }
            bar = FootprintBar(
                openTime = barStartTime,
                closeTime = barEndTime,
                open = tick.price,
                high = tick.price,
                low = tick.price,
                close = tick.price
            )
            currentBar = bar
        }

        // Update OHLC
        if (tick.price > bar.high) bar.high = tick.price
        if (tick.price < bar.low) bar.low = tick.price
        bar.close = tick.price
        bar.totalVolume += tick.quantity

        // Update Footprint Cluster
        val cluster = bar.clusters.getOrPut(roundedPrice) { FootprintCluster(roundedPrice) }
        val deltaVal = if (tick.side == TradeSide.BUY) {
            cluster.askVolume += tick.quantity
            cluster.askCount++
            tick.quantity
        } else {
            cluster.bidVolume += tick.quantity
            cluster.bidCount++
            -tick.quantity
        }

        bar.totalDelta += deltaVal
        if (bar.totalDelta < bar.minDelta) bar.minDelta = bar.totalDelta
        if (bar.totalDelta > bar.maxDelta) bar.maxDelta = bar.totalDelta

        return bar
    }

    fun getBars(): List<FootprintBar> {
        val list = ArrayList(completedBars)
        currentBar?.let { list.add(it) }
        return list
    }

    private fun roundToTick(price: Double): Double {
        return Math.round(price / tickSize) * tickSize
    }
}
