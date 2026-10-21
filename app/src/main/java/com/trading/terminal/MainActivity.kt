package com.trading.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.trading.terminal.core.data.model.*
import com.trading.terminal.core.data.rest.BinanceFuturesRestClient
import com.trading.terminal.core.data.websocket.BinanceFuturesWsClient
import com.trading.terminal.core.data.websocket.HyperliquidWsClient
import com.trading.terminal.core.engine.*
import com.trading.terminal.core.ui.theme.TerminalTheme
import com.trading.terminal.feature.dashboard.TerminalScreen
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private val restClient = BinanceFuturesRestClient()

    // Engines – reset on symbol change
    private var footprintEngine = FootprintEngine(barDurationMs = 60_000L, tickSize = 1.0)
    private var orderBookEngine = OrderBookEngine()
    private var heatmapEngine   = LiquidityHeatmapEngine()
    private var cvdEngine       = CVDEngine()

    // Active connection job – cancelled on symbol change
    private var connectionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TerminalTheme {
                var symbol   by remember { mutableStateOf("BTCUSDT") }
                var exchange by remember { mutableStateOf(ExchangeSource.BINANCE_FUTURES) }

                var currentPrice    by remember { mutableDoubleStateOf(0.0) }
                var priceChangePct  by remember { mutableDoubleStateOf(0.0) }
                var fundingRate     by remember { mutableDoubleStateOf(0.0) }
                var openInterestUsd by remember { mutableDoubleStateOf(0.0) }

                var bids          by remember { mutableStateOf<List<PriceLevel>>(emptyList()) }
                var asks          by remember { mutableStateOf<List<PriceLevel>>(emptyList()) }
                var recentTicks   by remember { mutableStateOf<List<TradeTick>>(emptyList()) }
                var footprintBars by remember { mutableStateOf<List<FootprintBar>>(emptyList()) }
                var heatmapPixels by remember { mutableStateOf<IntArray?>(null) }
                var liquidations  by remember { mutableStateOf<List<LiquidationEvent>>(emptyList()) }

                // Re-connect whenever symbol or exchange changes
                LaunchedEffect(symbol, exchange) {
                    // 1. Cancel previous connection completely
                    connectionJob?.cancelAndJoin()

                    // 2. Reset all state + engines for new symbol
                    currentPrice    = 0.0
                    priceChangePct  = 0.0
                    fundingRate     = 0.0
                    openInterestUsd = 0.0
                    bids            = emptyList()
                    asks            = emptyList()
                    recentTicks     = emptyList()
                    footprintBars   = emptyList()
                    heatmapPixels   = null
                    liquidations    = emptyList()

                    // 3. Create fresh engines for new symbol
                    footprintEngine = FootprintEngine(barDurationMs = 60_000L, tickSize = 1.0)
                    orderBookEngine = OrderBookEngine()
                    heatmapEngine   = LiquidityHeatmapEngine()
                    cvdEngine       = CVDEngine()

                    // 4. Launch new coroutine scope for this symbol
                    connectionJob = launch(Dispatchers.IO) {
                        if (exchange == ExchangeSource.BINANCE_FUTURES) {
                            connectBinance(symbol,
                                onPrice    = { withContext(Dispatchers.Main) { currentPrice    = it } },
                                onPct      = { withContext(Dispatchers.Main) { priceChangePct  = it } },
                                onFunding  = { withContext(Dispatchers.Main) { fundingRate     = it } },
                                onOI       = { withContext(Dispatchers.Main) { openInterestUsd = it } },
                                onBids     = { withContext(Dispatchers.Main) { bids            = it } },
                                onAsks     = { withContext(Dispatchers.Main) { asks            = it } },
                                onTicks    = { withContext(Dispatchers.Main) { recentTicks     = it } },
                                onBars     = { withContext(Dispatchers.Main) { footprintBars   = it } },
                                onHeatmap  = { withContext(Dispatchers.Main) { heatmapPixels   = it } },
                                onLiqs     = { withContext(Dispatchers.Main) { liquidations    = it } }
                            )
                        } else {
                            connectHyperliquid(symbol,
                                onPrice   = { withContext(Dispatchers.Main) { currentPrice  = it } },
                                onBids    = { withContext(Dispatchers.Main) { bids          = it } },
                                onAsks    = { withContext(Dispatchers.Main) { asks          = it } },
                                onTicks   = { withContext(Dispatchers.Main) { recentTicks   = it } },
                                onHeatmap = { withContext(Dispatchers.Main) { heatmapPixels = it } }
                            )
                        }
                    }
                }

                TerminalScreen(
                    currentSymbol    = symbol,
                    currentExchange  = exchange,
                    currentPrice     = currentPrice,
                    priceChangePct   = priceChangePct,
                    fundingRate      = fundingRate,
                    openInterestUsd  = openInterestUsd,
                    bids             = bids,
                    asks             = asks,
                    recentTicks      = recentTicks,
                    footprintBars    = footprintBars,
                    heatmapPixels    = heatmapPixels,
                    liquidations     = liquidations,
                    onSymbolSelected   = { if (it != symbol) symbol   = it },
                    onExchangeSelected = { if (it != exchange) exchange = it }
                )
            }
        }
    }

    // ─── Binance Futures connection block ────────────────────────────────────
    private suspend fun CoroutineScope.connectBinance(
        symbol: String,
        onPrice:   suspend (Double) -> Unit,
        onPct:     suspend (Double) -> Unit,
        onFunding: suspend (Double) -> Unit,
        onOI:      suspend (Double) -> Unit,
        onBids:    suspend (List<PriceLevel>) -> Unit,
        onAsks:    suspend (List<PriceLevel>) -> Unit,
        onTicks:   suspend (List<TradeTick>) -> Unit,
        onBars:    suspend (List<FootprintBar>) -> Unit,
        onHeatmap: suspend (IntArray) -> Unit,
        onLiqs:    suspend (List<LiquidationEvent>) -> Unit
    ) {
        // Step 1: REST snapshot to get initial price + DOM + historical candles
        try {
            val snap = restClient.getDepthSnapshot(symbol)
            val mid  = snap.midPrice
            if (mid > 0) {
                onPrice(mid)
                heatmapEngine.updatePriceRange(mid, 0.004)
                orderBookEngine.setSnapshot(snap)
                val deepBids = orderBookEngine.getTopBids(100)
                val deepAsks = orderBookEngine.getTopAsks(100)
                heatmapEngine.recordDepthSnapshot(deepBids, deepAsks)
                onBids(deepBids.take(30))
                onAsks(deepAsks.take(30))
                onHeatmap(heatmapEngine.bakePixelBuffer().copyOf())
            }

            // REST metrics (funding, OI, 24h change)
            val metrics = restClient.getMarketMetrics(symbol)
            onFunding(metrics.fundingRate)
            onOI(metrics.openInterestUsd)
            onPct(metrics.priceChangePct24h)
            if (metrics.markPrice > 0) {
                onPrice(metrics.markPrice)
                heatmapEngine.updatePriceRange(metrics.markPrice, 0.004)
            }

            // Seed historical 1m candles so chart is immediately populated
            val klines = restClient.getKlines(symbol, "1m", 30)
            if (klines.isNotEmpty()) {
                footprintEngine.initFromKlines(klines)
                onBars(footprintEngine.getBars())
            }
        } catch (e: Exception) {
            // Network failure – will still connect WS below
        }

        // Step 2: WebSocket streams
        val ws = BinanceFuturesWsClient(this)
        ws.connect(symbol)

        // Local mutable lists (thread-confined to this coroutine)
        val tickList = ArrayDeque<TradeTick>(210)
        val liqList  = ArrayDeque<LiquidationEvent>(25)

        // Tick collector
        val tickJob = launch {
            ws.ticks.collect { tick ->
                val newPrice = tick.price
                onPrice(newPrice)
                // Init heatmap range from first tick if REST failed
                if (heatmapEngine.minPrice <= 0) heatmapEngine.updatePriceRange(newPrice, 0.004)

                footprintEngine.onTick(tick)
                cvdEngine.onTick(tick)

                tickList.addLast(tick)
                if (tickList.size > 200) tickList.removeFirst()
                onTicks(tickList.toList())
                onBars(footprintEngine.getBars())
            }
        }

        // Liquidation collector
        val liqJob = launch {
            ws.liquidations.collect { liq ->
                liqList.addFirst(liq)
                if (liqList.size > 20) liqList.removeLast()
                onLiqs(liqList.toList())
            }
        }

        // DOM + Heatmap bake loop at 10fps
        val domJob = launch {
            while (isActive) {
                delay(100)
                val deepBids = orderBookEngine.getTopBids(100)
                val deepAsks = orderBookEngine.getTopAsks(100)
                onBids(deepBids.take(30))
                onAsks(deepAsks.take(30))
                if (heatmapEngine.minPrice > 0) {
                    heatmapEngine.recordDepthSnapshot(deepBids, deepAsks)
                    onHeatmap(heatmapEngine.bakePixelBuffer().copyOf())
                }
            }
        }

        // Depth diff collector (parse JSON and feed orderBookEngine)
        val depthJob = launch {
            ws.depthUpdates.collect { depthJson ->
                try {
                    val updateId = depthJson.optLong("u", 0L)
                    val bidsArr  = depthJson.optJSONArray("b") ?: return@collect
                    val asksArr  = depthJson.optJSONArray("a") ?: return@collect
                    val bidLevels = (0 until bidsArr.length()).map {
                        val row = bidsArr.getJSONArray(it)
                        PriceLevel(row.getString(0).toDouble(), row.getString(1).toDouble())
                    }
                    val askLevels = (0 until asksArr.length()).map {
                        val row = asksArr.getJSONArray(it)
                        PriceLevel(row.getString(0).toDouble(), row.getString(1).toDouble())
                    }
                    orderBookEngine.applyDiff(bidLevels, askLevels, updateId)
                } catch (_: Exception) {}
            }
        }

        // Wait for cancellation (when symbol changes)
        try {
            awaitCancellation()
        } finally {
            ws.disconnect()
            tickJob.cancel(); liqJob.cancel(); domJob.cancel(); depthJob.cancel()
        }
    }

    // ─── Hyperliquid DEX connection block ─────────────────────────────────────
    private suspend fun CoroutineScope.connectHyperliquid(
        symbol: String,
        onPrice:   suspend (Double) -> Unit,
        onBids:    suspend (List<PriceLevel>) -> Unit,
        onAsks:    suspend (List<PriceLevel>) -> Unit,
        onTicks:   suspend (List<TradeTick>) -> Unit,
        onHeatmap: suspend (IntArray) -> Unit
    ) {
        val hlSymbol = symbol.removeSuffix("USDT") // "BTCUSDT" -> "BTC"
        val ws = HyperliquidWsClient()
        ws.connect(hlSymbol)

        val tickList = ArrayDeque<TradeTick>(210)

        val tickJob = launch {
            ws.ticks.collect { tick ->
                onPrice(tick.price)
                if (heatmapEngine.minPrice <= 0) heatmapEngine.updatePriceRange(tick.price)
                tickList.addLast(tick)
                if (tickList.size > 200) tickList.removeFirst()
                onTicks(tickList.toList())
            }
        }

        val bookJob = launch {
            ws.l2BookUpdates.collect { ob ->
                val b = ob.bids.take(30)
                val a = ob.asks.take(30)
                onBids(b); onAsks(a)
                val mid = ob.midPrice
                if (mid > 0 && heatmapEngine.minPrice <= 0) heatmapEngine.updatePriceRange(mid)
                if (heatmapEngine.minPrice > 0) {
                    heatmapEngine.recordDepthSnapshot(b, a)
                    onHeatmap(heatmapEngine.bakePixelBuffer().copyOf())
                }
            }
        }

        try {
            awaitCancellation()
        } finally {
            ws.disconnect()
            tickJob.cancel(); bookJob.cancel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionJob?.cancel()
    }
}
