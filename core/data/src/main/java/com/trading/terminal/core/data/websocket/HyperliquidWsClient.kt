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

class HyperliquidWsClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var activeWebSocket: WebSocket? = null

    private val _ticks = MutableSharedFlow<TradeTick>(extraBufferCapacity = 5000)
    val ticks: SharedFlow<TradeTick> = _ticks.asSharedFlow()

    private val _l2BookUpdates = MutableSharedFlow<OrderBook>(extraBufferCapacity = 1000)
    val l2BookUpdates: SharedFlow<OrderBook> = _l2BookUpdates.asSharedFlow()

    fun connect(coin: String = "BTC") {
        disconnect()

        val request = Request.Builder()
            .url("wss://api.hyperliquid.xyz/ws")
            .build()

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Subscribe to Trades
                val subTrades = JSONObject().apply {
                    put("method", "subscribe")
                    put("subscription", JSONObject().apply {
                        put("type", "trades")
                        put("coin", coin.uppercase())
                    })
                }
                webSocket.send(subTrades.toString())

                // Subscribe to L2 Book
                val subBook = JSONObject().apply {
                    put("method", "subscribe")
                    put("subscription", JSONObject().apply {
                        put("type", "l2Book")
                        put("coin", coin.uppercase())
                    })
                }
                webSocket.send(subBook.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    val channel = root.optString("channel")
                    val data = root.optJSONObject("data") ?: return

                    when (channel) {
                        "trades" -> {
                            val tradesArr = root.optJSONArray("data")
                            if (tradesArr != null) {
                                for (i in 0 until tradesArr.length()) {
                                    val item = tradesArr.getJSONObject(i)
                                    val px = item.getDouble("px")
                                    val sz = item.getDouble("sz")
                                    val sideStr = item.getString("side") // "B" or "A"
                                    val time = item.getLong("time")
                                    val hash = item.optLong("tid", 0L)

                                    val tick = TradeTick(
                                        exchange = ExchangeSource.HYPERLIQUID,
                                        symbol = coin.uppercase(),
                                        price = px,
                                        quantity = sz,
                                        timestamp = time,
                                        side = if (sideStr == "B") TradeSide.BUY else TradeSide.SELL,
                                        tradeId = hash
                                    )
                                    _ticks.tryEmit(tick)
                                }
                            }
                        }
                        "l2Book" -> {
                            val levels = data.optJSONArray("levels") ?: return
                            if (levels.length() >= 2) {
                                val bidsArr = levels.getJSONArray(0)
                                val asksArr = levels.getJSONArray(1)
                                val time = data.optLong("time", System.currentTimeMillis())

                                val bids = ArrayList<PriceLevel>()
                                for (i in 0 until bidsArr.length()) {
                                    val lvl = bidsArr.getJSONObject(i)
                                    bids.add(PriceLevel(lvl.getDouble("px"), lvl.getDouble("sz"), lvl.getInt("n")))
                                }

                                val asks = ArrayList<PriceLevel>()
                                for (i in 0 until asksArr.length()) {
                                    val lvl = asksArr.getJSONObject(i)
                                    asks.add(PriceLevel(lvl.getDouble("px"), lvl.getDouble("sz"), lvl.getInt("n")))
                                }

                                val ob = OrderBook(
                                    symbol = coin.uppercase(),
                                    bids = bids,
                                    asks = asks,
                                    timestamp = time
                                )
                                _l2BookUpdates.tryEmit(ob)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parse error
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch {
                    kotlinx.coroutines.delay(2500)
                    connect(coin)
                }
            }
        })
    }

    fun disconnect() {
        activeWebSocket?.close(1000, "Normal closure")
        activeWebSocket = null
    }
}
