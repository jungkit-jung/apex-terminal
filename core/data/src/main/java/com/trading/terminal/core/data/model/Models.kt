package com.trading.terminal.core.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExchangeSource {
    BINANCE_FUTURES,
    BINANCE_SPOT,
    HYPERLIQUID,
    DYDX_V4
}

@Serializable
enum class TradeSide {
    BUY,   // Taker Buy (Green)
    SELL   // Taker Sell (Red)
}

/**
 * Raw trade tick (tick-by-tick market execution)
 */
@Serializable
data class TradeTick(
    val exchange: ExchangeSource,
    val symbol: String,
    val price: Double,
    val quantity: Double,
    val quoteQuantity: Double = price * quantity,
    val timestamp: Long,
    val side: TradeSide,
    val tradeId: Long = 0L
)

/**
 * Level 2 Order Book Price Row
 */
@Serializable
data class PriceLevel(
    val price: Double,
    val quantity: Double,
    val orderCount: Int = 1
)

/**
 * Full L2 Order Book state
 */
@Serializable
data class OrderBook(
    val symbol: String,
    val bids: List<PriceLevel>, // Descending by price
    val asks: List<PriceLevel>, // Ascending by price
    val timestamp: Long,
    val lastUpdateId: Long = 0L
) {
    val bestBid: Double? get() = bids.firstOrNull()?.price
    val bestAsk: Double? get() = asks.firstOrNull()?.price
    val midPrice: Double get() = ((bestBid ?: 0.0) + (bestAsk ?: 0.0)) / 2.0
    val spread: Double get() = (bestAsk ?: 0.0) - (bestBid ?: 0.0)
}

/**
 * Liquidation event stream
 */
@Serializable
data class LiquidationEvent(
    val symbol: String,
    val side: TradeSide, // BUY = Short liquidated, SELL = Long liquidated
    val price: Double,
    val quantity: Double,
    val totalUsd: Double,
    val timestamp: Long
)

/**
 * Open Interest & Sentiment data
 */
@Serializable
data class MarketMetrics(
    val symbol: String,
    val openInterest: Double,
    val openInterestUsd: Double,
    val fundingRate: Double,
    val nextFundingTime: Long,
    val topTraderLongShortRatio: Double = 1.0,
    val globalLongShortRatio: Double = 1.0
)
