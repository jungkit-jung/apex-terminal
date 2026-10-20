package com.trading.terminal.core.engine

import com.trading.terminal.core.data.model.OrderBook
import com.trading.terminal.core.data.model.PriceLevel
import java.util.concurrent.ConcurrentSkipListMap

class OrderBookEngine {
    // Thread-safe ordered maps for Bids (descending) and Asks (ascending)
    private val bidsMap = ConcurrentSkipListMap<Double, Double>(Comparator.reverseOrder())
    private val asksMap = ConcurrentSkipListMap<Double, Double>()

    private var lastUpdateId: Long = 0L

    fun setSnapshot(snapshot: OrderBook) {
        bidsMap.clear()
        asksMap.clear()
        lastUpdateId = snapshot.lastUpdateId

        for (lvl in snapshot.bids) {
            if (lvl.quantity > 0) bidsMap[lvl.price] = lvl.quantity
        }
        for (lvl in snapshot.asks) {
            if (lvl.quantity > 0) asksMap[lvl.price] = lvl.quantity
        }
    }

    fun applyDiff(bids: List<PriceLevel>, asks: List<PriceLevel>, updateId: Long = 0L) {
        if (updateId > 0 && updateId <= lastUpdateId) return
        lastUpdateId = updateId

        for (lvl in bids) {
            if (lvl.quantity <= 0.0) {
                bidsMap.remove(lvl.price)
            } else {
                bidsMap[lvl.price] = lvl.quantity
            }
        }

        for (lvl in asks) {
            if (lvl.quantity <= 0.0) {
                asksMap.remove(lvl.price)
            } else {
                asksMap[lvl.price] = lvl.quantity
            }
        }
    }

    /**
     * Compute Book Imbalance: (TotalBid - TotalAsk) / (TotalBid + TotalAsk)
     * Range: -1.0 (100% Sell Pressure) to +1.0 (100% Buy Pressure)
     */
    fun calculateImbalance(depthLevels: Int = 20): Double {
        var bidSum = 0.0
        var askSum = 0.0

        bidsMap.values.take(depthLevels).forEach { bidSum += it }
        asksMap.values.take(depthLevels).forEach { askSum += it }

        val total = bidSum + askSum
        return if (total > 0) (bidSum - askSum) / total else 0.0
    }

    fun getTopBids(count: Int = 30): List<PriceLevel> {
        return bidsMap.entries.take(count).map { PriceLevel(it.key, it.value) }
    }

    fun getTopAsks(count: Int = 30): List<PriceLevel> {
        return asksMap.entries.take(count).map { PriceLevel(it.key, it.value) }
    }

    fun getSpread(): Double {
        val bestBid = bidsMap.firstKey() ?: 0.0
        val bestAsk = asksMap.firstKey() ?: 0.0
        return if (bestBid > 0 && bestAsk > 0) bestAsk - bestBid else 0.0
    }
}
