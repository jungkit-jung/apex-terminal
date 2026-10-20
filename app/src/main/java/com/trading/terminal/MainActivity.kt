package com.trading.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.trading.terminal.core.data.model.*
import com.trading.terminal.core.data.rest.BinanceFuturesRestClient
import com.trading.terminal.core.data.websocket.BinanceFuturesWsClient
import com.trading.terminal.core.data.websocket.HyperliquidWsClient
import com.trading.terminal.core.engine.*
import com.trading.terminal.core.ui.theme.TerminalTheme
import com.trading.terminal.feature.dashboard.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val binanceWs = BinanceFuturesWsClient()
    private val hyperliquidWs = HyperliquidWsClient()
    private val restClient = BinanceFuturesRestClient()

    private val footprintEngine = FootprintEngine(barDurationMs = 60_000L, tickSize = 1.0)
    private val orderBookEngine = OrderBookEngine()
    private val heatmapEngine = LiquidityHeatmapEngine()
    private val cvdEngine = CVDEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TerminalTheme {
                var symbol by remember { mutableStateOf("BTCUSDT") }
                var exchange by remember { mutableStateOf(ExchangeSource.BINANCE_FUTURES) }
                var currentPrice by remember { mutableDoubleStateOf(68500.0) }
                var priceChangePct by remember { mutableDoubleStateOf(2.45) }
                var fundingRate by remember { mutableDoubleStateOf(0.0001) }
                var openInterestUsd by remember { mutableDoubleStateOf(450_000_000.0) }

                var bids by remember { mutableStateOf<List<PriceLevel>>(emptyList()) }
                var asks by remember { mutableStateOf<List<PriceLevel>>(emptyList()) }
                var recentTicks by remember { mutableStateOf<List<TradeTick>>(emptyList()) }
                var footprintBars by remember { mutableStateOf<List<FootprintBar>>(emptyList()) }
                var heatmapPixels by remember { mutableStateOf<IntArray?>(null) }
                var liquidations by remember { mutableStateOf<List<LiquidationEvent>>(emptyList()) }

                // Connect streams and setup real-time collectors
                LaunchedEffect(symbol, exchange) {
                    if (exchange == ExchangeSource.BINANCE_FUTURES) {
                        // 1. Fetch initial snapshot from REST
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val snap = restClient.getDepthSnapshot(symbol)
                                orderBookEngine.setSnapshot(snap)
                                val metrics = restClient.getMarketMetrics(symbol)
                                withContext(Dispatchers.Main) {
                                    bids = orderBookEngine.getTopBids(30)
                                    asks = orderBookEngine.getTopAsks(30)
                                    fundingRate = metrics.fundingRate
                                    openInterestUsd = metrics.openInterestUsd
                                    heatmapEngine.updatePriceRange(snap.midPrice)
                                }
                            } catch (e: Exception) {
                                // fallback
                            }
                        }

                        // 2. Connect Binance WS
                        binanceWs.connect(symbol)

                        // Collect Ticks
                        launch {
                            binanceWs.ticks.collect { tick ->
                                currentPrice = tick.price
                                footprintEngine.onTick(tick)
                                cvdEngine.onTick(tick)

                                val tickList = ArrayList(recentTicks)
                                tickList.add(tick)
                                if (tickList.size > 200) tickList.removeAt(0)
                                recentTicks = tickList
                                footprintBars = footprintEngine.getBars()
                            }
                        }

                        // Collect Liquidations
                        launch {
                            binanceWs.liquidations.collect { liq ->
                                val liqList = ArrayList(liquidations)
                                liqList.add(0, liq)
                                if (liqList.size > 20) liqList.removeAt(liqList.size - 1)
                                liquidations = liqList
                            }
                        }

                        // Periodic Heatmap & DOM Bake Loop
                        launch {
                            while (true) {
                                kotlinx.coroutines.delay(100) // 10 fps DOM & Heatmap update
                                bids = orderBookEngine.getTopBids(30)
                                asks = orderBookEngine.getTopAsks(30)
                                heatmapEngine.recordDepthSnapshot(bids, asks)
                                heatmapPixels = heatmapEngine.bakePixelBuffer().clone()
                            }
                        }
                    } else {
                        // Connect Hyperliquid DEX
                        hyperliquidWs.connect("BTC")
                        launch {
                            hyperliquidWs.ticks.collect { tick ->
                                currentPrice = tick.price
                                val tickList = ArrayList(recentTicks)
                                tickList.add(tick)
                                if (tickList.size > 200) tickList.removeAt(0)
                                recentTicks = tickList
                            }
                        }
                        launch {
                            hyperliquidWs.l2BookUpdates.collect { ob ->
                                bids = ob.bids.take(30)
                                asks = ob.asks.take(30)
                                heatmapEngine.recordDepthSnapshot(bids, asks)
                                heatmapPixels = heatmapEngine.bakePixelBuffer().clone()
                            }
                        }
                    }
                }

                TerminalScreen(
                    currentSymbol = symbol,
                    currentExchange = exchange,
                    currentPrice = currentPrice,
                    priceChangePct = priceChangePct,
                    fundingRate = fundingRate,
                    openInterestUsd = openInterestUsd,
                    bids = bids,
                    asks = asks,
                    recentTicks = recentTicks,
                    footprintBars = footprintBars,
                    heatmapPixels = heatmapPixels,
                    liquidations = liquidations,
                    onSymbolSelected = { symbol = it },
                    onExchangeSelected = { exchange = it }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binanceWs.disconnect()
        hyperliquidWs.disconnect()
    }
}
