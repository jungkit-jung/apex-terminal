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
                .padding(horizontal = 8.dp, vertical = 5.dp),
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

        // Imbalance bar (green=bid, red=ask)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
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
                .padding(vertical = 3.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("BID", color = ElectricEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("PRICE", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("ASK", color = VibrantCrimson, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }

        // ─── Asks (top half, reversed so best ask is at center) ───────────
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = askListState,
            reverseLayout = true
        ) {
            items(asks.take(20), key = { it.price }) { ask ->
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
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Best bid price + spread + best ask price
                Text(
                    text = if (currentPrice > 1000)
                        String.format("%,.1f", currentPrice)
                    else
                        String.format("%.4f", currentPrice),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (spread > 0) {
                    Spacer(Modifier.width(4.dp))
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
            items(bids.take(20), key = { it.price }) { bid ->
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
                .height(20.dp)
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
                    text = "B: ${String.format("%.1f", bidVol)}",
                    color = ElectricEmerald.copy(alpha = 0.8f),
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
                    text = "A: ${String.format("%.1f", askVol)}",
                    color = VibrantCrimson.copy(alpha = 0.8f),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .background(if (isCurrentPrice) TerminalBorder else Color.Transparent)
    ) {
        // Depth bar fills from edge toward center
        if (bidQty != null && maxQty > 0) {
            val barRatio = (bidQty / maxQty).toFloat().coerceIn(0f, 1f)
            // Use a logarithmic scaling for better visual
            val logRatio = (kotlin.math.ln(1.0 + bidQty) / kotlin.math.ln(1.0 + maxQty)).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(logRatio * 0.5f)
                    .align(Alignment.CenterStart)
                    .background(ElectricEmerald.copy(alpha = 0.18f))
            )
        }

        if (askQty != null && maxQty > 0) {
            val logRatio = (kotlin.math.ln(1.0 + askQty) / kotlin.math.ln(1.0 + maxQty)).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(logRatio * 0.5f)
                    .align(Alignment.CenterEnd)
                    .background(VibrantCrimson.copy(alpha = 0.18f))
            )
        }

        // Row content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bid qty
            Text(
                text = bidQty?.let { formatQty(it) } ?: "",
                color = if (bidQty != null && bidQty > maxQty * 0.5) ElectricEmerald else ElectricEmerald.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )

            // Price (center)
            Text(
                text = if (price > 1000) String.format("%,.1f", price) else String.format("%.4f", price),
                color = if (isCurrentPrice) Color.White else TextPrimary,
                fontSize = 9.sp,
                fontWeight = if (isCurrentPrice) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1.2f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Ask qty
            Text(
                text = askQty?.let { formatQty(it) } ?: "",
                color = if (askQty != null && askQty > maxQty * 0.5) VibrantCrimson else VibrantCrimson.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

private fun formatQty(qty: Double): String = when {
    qty >= 1000.0 -> String.format("%.0fK", qty / 1000.0)
    qty >= 10.0   -> String.format("%.1f", qty)
    else          -> String.format("%.2f", qty)
}
