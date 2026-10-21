package com.trading.terminal.feature.dom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trading.terminal.core.data.model.PriceLevel
import com.trading.terminal.core.ui.theme.*

@Composable
fun DOMLadderView(
    bids: List<PriceLevel>,
    asks: List<PriceLevel>,
    imbalanceRatio: Double, // -1.0 to 1.0
    currentPrice: Double,
    modifier: Modifier = Modifier
) {
    val maxBidQty = (bids.maxOfOrNull { it.quantity } ?: 1.0).coerceAtLeast(0.01)
    val maxAskQty = (asks.maxOfOrNull { it.quantity } ?: 1.0).coerceAtLeast(0.01)
    val maxOverall = maxOf(maxBidQty, maxAskQty)

    val askListState = rememberLazyListState()
    val bidListState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalSurface)
    ) {
        // ─── Header: DOM Label + Imbalance ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalPanel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOM L2",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            val imbColor = if (imbalanceRatio >= 0) ElectricEmerald else VibrantCrimson
            val imbText = String.format("%+.1f%%", imbalanceRatio * 100)
            Text(
                text = "Imb $imbText",
                color = imbColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Imbalance meter bar (green=bid, red=ask)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
        ) {
            val buyPct = ((imbalanceRatio + 1.0) / 2.0).toFloat().coerceIn(0.01f, 0.99f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(buyPct)
                    .background(ElectricEmerald)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f - buyPct)
                    .background(VibrantCrimson)
            )
        }

        // Column Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalBackground)
                .padding(vertical = 3.dp, horizontal = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BID",
                color = ElectricEmerald,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "PRICE",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1.2f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "ASK",
                color = VibrantCrimson,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }

        // ─── Asks (top half, reversed so best ask is next to center) ───────
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = askListState,
            reverseLayout = true
        ) {
            items(asks.take(25), key = { it.price }) { ask ->
                DOMRow(
                    price = ask.price,
                    bidQty = null,
                    askQty = ask.quantity,
                    maxQty = maxOverall,
                    isCurrentPrice = false
                )
            }
        }

        // ─── Spread Center ─────────────────────────────────────────────────
        val bestBid = bids.firstOrNull()?.price ?: 0.0
        val bestAsk = asks.firstOrNull()?.price ?: 0.0
        val spread = (bestAsk - bestBid).coerceAtLeast(0.0)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalPanel)
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentPrice >= 1000)
                        String.format("%,.1f", currentPrice)
                    else
                        String.format("%.4f", currentPrice),
                    color = ElectricEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (spread > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = String.format("±%.2f", spread),
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // ─── Bids (bottom half) ────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = bidListState
        ) {
            items(bids.take(25), key = { it.price }) { bid ->
                DOMRow(
                    price = bid.price,
                    bidQty = bid.quantity,
                    askQty = null,
                    maxQty = maxOverall,
                    isCurrentPrice = false
                )
            }
        }

        // ─── CVD / Volume Delta mini strip ───────────────────────────────
        val bidVol = bids.take(5).sumOf { it.quantity }
        val askVol = asks.take(5).sumOf { it.quantity }
        val totalVol = bidVol + askVol
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(TerminalBackground)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "B: ${formatQty(bidVol)}",
                    color = ElectricEmerald.copy(alpha = 0.85f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (totalVol > 0) {
                    val bidPct = (bidVol / totalVol * 100).toInt()
                    Text(
                        text = "$bidPct% / ${100-bidPct}%",
                        color = TextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "A: ${formatQty(askVol)}",
                    color = VibrantCrimson.copy(alpha = 0.85f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun DOMRow(
    price: Double,
    bidQty: Double?,
    askQty: Double?,
    maxQty: Double,
    isCurrentPrice: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(19.dp)
            .background(if (isCurrentPrice) TerminalPanel else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. BID CELL (depth bar stays strictly inside this column)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            if (bidQty != null && maxQty > 0) {
                val logRatio = (kotlin.math.ln(1.0 + bidQty) / kotlin.math.ln(1.0 + maxQty)).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(logRatio)
                        .align(Alignment.CenterEnd)
                        .background(ElectricEmerald.copy(alpha = 0.22f))
                )
            }
            Text(
                text = bidQty?.let { formatQty(it) } ?: "",
                color = if (bidQty != null && bidQty > maxQty * 0.4) ElectricEmerald else ElectricEmerald.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (bidQty != null && bidQty > maxQty * 0.4) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // 2. PRICE CELL (center, completely isolated from depth bars)
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .background(if (isCurrentPrice) TerminalBorder else TerminalSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (price >= 1000) String.format("%,.1f", price) else String.format("%.4f", price),
                color = if (isCurrentPrice) Color.White else TextPrimary,
                fontSize = 9.5.sp,
                fontWeight = if (isCurrentPrice) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }

        // 3. ASK CELL (depth bar stays strictly inside this column)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (askQty != null && maxQty > 0) {
                val logRatio = (kotlin.math.ln(1.0 + askQty) / kotlin.math.ln(1.0 + maxQty)).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(logRatio)
                        .align(Alignment.CenterStart)
                        .background(VibrantCrimson.copy(alpha = 0.22f))
                )
            }
            Text(
                text = askQty?.let { formatQty(it) } ?: "",
                color = if (askQty != null && askQty > maxQty * 0.4) VibrantCrimson else VibrantCrimson.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (askQty != null && askQty > maxQty * 0.4) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

/**
 * Format quantity dynamically without truncating fractional crypto orders to 0.00
 */
private fun formatQty(qty: Double): String = when {
    qty >= 1000.0 -> String.format("%.1fK", qty / 1000.0)
    qty >= 100.0  -> String.format("%.0f", qty)
    qty >= 10.0   -> String.format("%.1f", qty)
    qty >= 1.0    -> String.format("%.2f", qty)
    qty >= 0.01   -> String.format("%.3f", qty)
    else          -> String.format("%.4f", qty)
}
