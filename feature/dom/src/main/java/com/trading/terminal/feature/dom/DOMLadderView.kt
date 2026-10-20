package com.trading.terminal.feature.dom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(TerminalSurface)
    ) {
        // 1. Header: Imbalance Meter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOM L2",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            val imbColor = if (imbalanceRatio >= 0) ElectricEmerald else VibrantCrimson
            val imbText = String.format("%+.1f%%", imbalanceRatio * 100)
            Text(
                text = "Imb: $imbText",
                color = imbColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Horizontal Imbalance Bar Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(TerminalBorder)
        ) {
            val buyPct = ((imbalanceRatio + 1.0) / 2.0).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(buyPct.coerceAtLeast(0.01f))
                    .background(ElectricEmerald)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight((1f - buyPct).coerceAtLeast(0.01f))
                    .background(VibrantCrimson)
            )
        }

        // Column Titles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalPanel)
                .padding(vertical = 4.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("BID", color = ElectricEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("PRICE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("ASK", color = VibrantCrimson, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // Asks (Reverse so lowest ask is near center)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            reverseLayout = true
        ) {
            items(asks.take(25)) { ask ->
                DOMRow(
                    price = ask.price,
                    bidQty = null,
                    askQty = ask.quantity,
                    maxQty = maxOverall,
                    isCurrentPrice = (ask.price == currentPrice)
                )
            }
        }

        // Center Spread Indicator
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
            Text(
                text = String.format("Spread: %.2f", spread),
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Bids
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(bids.take(25)) { bid ->
                DOMRow(
                    price = bid.price,
                    bidQty = bid.quantity,
                    askQty = null,
                    maxQty = maxOverall,
                    isCurrentPrice = (bid.price == currentPrice)
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
            .height(20.dp)
            .background(if (isCurrentPrice) TerminalBorder else Color.Transparent)
    ) {
        // Depth Bar Graphic
        if (bidQty != null && maxQty > 0) {
            val barRatio = (bidQty / maxQty).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barRatio * 0.45f)
                    .align(Alignment.CenterStart)
                    .background(ElectricEmerald.copy(alpha = 0.20f))
            )
        }

        if (askQty != null && maxQty > 0) {
            val barRatio = (askQty / maxQty).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barRatio * 0.45f)
                    .align(Alignment.CenterEnd)
                    .background(VibrantCrimson.copy(alpha = 0.20f))
            )
        }

        // Data Row Text
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bid Qty
            Text(
                text = bidQty?.let { String.format("%.2f", it) } ?: "",
                color = ElectricEmerald,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(55.dp)
            )

            // Center Price
            Text(
                text = String.format("%.1f", price),
                color = if (isCurrentPrice) Color.White else TextPrimary,
                fontSize = 10.sp,
                fontWeight = if (isCurrentPrice) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace
            )

            // Ask Qty
            Text(
                text = askQty?.let { String.format("%.2f", it) } ?: "",
                color = VibrantCrimson,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(55.dp)
            )
        }
    }
}
