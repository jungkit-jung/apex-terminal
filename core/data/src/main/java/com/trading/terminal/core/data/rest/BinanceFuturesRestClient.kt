package com.trading.terminal.core.data.rest

import com.trading.terminal.core.data.model.MarketMetrics
import com.trading.terminal.core.data.model.OrderBook
import com.trading.terminal.core.data.model.PriceLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BinanceFuturesRestClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://fapi.binance.com"

    suspend fun getDepthSnapshot(symbol: String = "BTCUSDT", limit: Int = 500): OrderBook = withContext(Dispatchers.IO) {
        val url = "$baseUrl/fapi/v1/depth?symbol=${symbol.uppercase()}&limit=$limit"
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string() ?: "{}")
            val lastUpdateId = json.optLong("lastUpdateId", 0L)
            val time = json.optLong("E", System.currentTimeMillis())

            val bids = ArrayList<PriceLevel>()
            val bidsArr = json.optJSONArray("bids") ?: JSONArray()
            for (i in 0 until bidsArr.length()) {
                val row = bidsArr.getJSONArray(i)
                bids.add(PriceLevel(row.getString(0).toDouble(), row.getString(1).toDouble()))
            }

            val asks = ArrayList<PriceLevel>()
            val asksArr = json.optJSONArray("asks") ?: JSONArray()
            for (i in 0 until asksArr.length()) {
                val row = asksArr.getJSONArray(i)
                asks.add(PriceLevel(row.getString(0).toDouble(), row.getString(1).toDouble()))
            }

            OrderBook(
                symbol = symbol.uppercase(),
                bids = bids,
                asks = asks,
                timestamp = time,
                lastUpdateId = lastUpdateId
            )
        }
    }

    suspend fun getMarketMetrics(symbol: String = "BTCUSDT"): MarketMetrics = withContext(Dispatchers.IO) {
        val sym = symbol.uppercase()
        // 1. Open Interest
        val oiReq = Request.Builder().url("$baseUrl/fapi/v1/openInterest?symbol=$sym").build()
        var oi = 0.0
        client.newCall(oiReq).execute().use { res ->
            val json = JSONObject(res.body?.string() ?: "{}")
            oi = json.optString("openInterest", "0.0").toDouble()
        }

        // 2. Premium Index / Funding Rate
        val fundReq = Request.Builder().url("$baseUrl/fapi/v1/premiumIndex?symbol=$sym").build()
        var funding = 0.0
        var markPrice = 0.0
        var nextFunding = 0L
        client.newCall(fundReq).execute().use { res ->
            val json = JSONObject(res.body?.string() ?: "{}")
            funding = json.optString("lastFundingRate", "0.0").toDouble()
            markPrice = json.optString("markPrice", "0.0").toDouble()
            nextFunding = json.optLong("nextFundingTime", 0L)
        }

        // 3. Top Trader Long/Short Ratio
        val ratioReq = Request.Builder().url("$baseUrl/futures/data/topLongShortPositionRatio?symbol=$sym&period=5m&limit=1").build()
        var topRatio = 1.0
        try {
            client.newCall(ratioReq).execute().use { res ->
                val arr = JSONArray(res.body?.string() ?: "[]")
                if (arr.length() > 0) {
                    topRatio = arr.getJSONObject(0).optString("longShortRatio", "1.0").toDouble()
                }
            }
        } catch (e: Exception) {
            // optional
        }

        MarketMetrics(
            symbol = sym,
            openInterest = oi,
            openInterestUsd = oi * markPrice,
            fundingRate = funding,
            nextFundingTime = nextFunding,
            topTraderLongShortRatio = topRatio
        )
    }
}
