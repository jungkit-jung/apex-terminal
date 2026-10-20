package com.trading.terminal.core.data.websocket

import com.trading.terminal.core.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BinanceFuturesWsClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var activeWebSocket: WebSocket? = null

    private val _ticks = MutableSharedFlow<TradeTick>(extraBufferCapacity = 5000)
    val ticks: SharedFlow<TradeTick> = _ticks.asSharedFlow()

    private val _depthUpdates = MutableSharedFlow<JSONObject>(extraBufferCapacity = 2000)
    val depthUpdates: SharedFlow<JSONObject> = _depthUpdates.asSharedFlow()

    private val _liquidations = MutableSharedFlow<LiquidationEvent>(extraBufferCapacity = 500)
    val liquidations: SharedFlow<LiquidationEvent> = _liquidations.asSharedFlow()

    /**
     * Connect to Binance USD-M Futures combined WebSocket streams for a symbol.
     * Streams:
     * - <symbol>@aggTrade (Tick trades)
     * - <symbol>@depth@100ms (High-frequency L2 order book diff)
     * - <symbol>@forceOrder (Liquidation order stream)
     */
    fun connect(symbol: String = "btcusdt") {
        disconnect()

        val lowerSymbol = symbol.lowercase()
        // Binance Combined Stream URL
        val streams = "$lowerSymbol@aggTrade/$lowerSymbol@depth@100ms/$lowerSymbol@forceOrder"
        val wsUrl = "wss://fstream.binance.com/stream?streams=$streams"

        val request = Request.Builder().url(wsUrl).build()

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    val data = root.optJSONObject("data") ?: return
                    val eventType = data.optString("e")

                    when (eventType) {
                        "aggTrade" -> handleAggTrade(data)
                        "depthUpdate" -> handleDepthUpdate(data)
                        "forceOrder" -> handleForceOrder(data)
                    }
                } catch (e: Exception) {
                    // Ignore parse anomaly
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Auto-reconnect after backoff
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    connect(symbol)
                }
            }
        })
    }

    private fun handleAggTrade(data: JSONObject) {
        val sym = data.getString("s")
        val price = data.getString("p").toDouble()
        val qty = data.getString("q").toDouble()
        val isBuyerMaker = data.getBoolean("m") // if true, seller is taker (SELL)
        val time = data.getLong("T")
        val id = data.getLong("a")

        val tick = TradeTick(
            exchange = ExchangeSource.BINANCE_FUTURES,
            symbol = sym,
            price = price,
            quantity = qty,
            timestamp = time,
            side = if (isBuyerMaker) TradeSide.SELL else TradeSide.BUY,
            tradeId = id
        )
        _ticks.tryEmit(tick)
    }

    private fun handleDepthUpdate(data: JSONObject) {
        _depthUpdates.tryEmit(data)
    }

    private fun handleForceOrder(data: JSONObject) {
        val o = data.getJSONObject("o")
        val sym = o.getString("s")
        val sideStr = o.getString("S")
        val price = o.getString("p").toDouble()
        val qty = o.getString("q").toDouble()
        val time = data.getLong("E")

        val liq = LiquidationEvent(
            symbol = sym,
            side = if (sideStr == "BUY") TradeSide.BUY else TradeSide.SELL,
            price = price,
            quantity = qty,
            totalUsd = price * qty,
            timestamp = time
        )
        _liquidations.tryEmit(liq)
    }

    fun disconnect() {
        activeWebSocket?.close(1000, "Normal closure")
        activeWebSocket = null
    }
}
