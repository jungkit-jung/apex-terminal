package com.trading.terminal.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trading.terminal.core.data.model.*
import com.trading.terminal.core.engine.*
import com.trading.terminal.core.ui.theme.*
import com.trading.terminal.feature.chart.BookmapHeatmapView
import com.trading.terminal.feature.chart.FootprintChartView
import com.trading.terminal.feature.dom.DOMLadderView

enum class TerminalDisplayMode {
    BOOKMAP_HEATMAP,
    SIERRA_FOOTPRINT,
    SPLIT_DOM
}

val SYMBOL_LIST = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT")

@Composable
fun TerminalScreen(
    currentSymbol: String,
    currentExchange: ExchangeSource,
    currentPrice: Double,
    priceChangePct: Double,
    fundingRate: Double,
    openInterestUsd: Double,
    bids: List<PriceLevel>,
    asks: List<PriceLevel>,
    recentTicks: List<TradeTick>,
    footprintBars: List<FootprintBar>,
    heatmapPixels: IntArray?,
    liquidations: List<LiquidationEvent>,
    onSymbolSelected: (String) -> Unit,
    onExchangeSelected: (ExchangeSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayMode by remember { mutableStateOf(TerminalDisplayMode.SPLIT_DOM) }

    // Dynamic price bounds matching Heatmap Engine span (0.004 = 0.4% centered on midPrice)
    val midPrice = if (currentPrice > 0) currentPrice else (bids.firstOrNull()?.price ?: 80000.0)
    val span = maxOf(midPrice * 0.004, 20.0)
    val minP = midPrice - span
    val maxP = midPrice + span

    // DOM Imbalance
    val totalBidQty = bids.take(20).sumOf { it.quantity }
    val totalAskQty = asks.take(20).sumOf { it.quantity }
    val domImbalance = if (totalBidQty + totalAskQty > 0) {
        (totalBidQty - totalAskQty) / (totalBidQty + totalAskQty)
    } else 0.0

    val isPositive = priceChangePct >= 0
    val priceColor = if (isPositive) ElectricEmerald else VibrantCrimson

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
    ) {
        // ─── TOP HEADER: Symbol + Price row ───────────────────────────────
        Surface(
            color = TerminalSurface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Symbol selector + Price
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Symbol + Exchange badge
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentSymbol.removeSuffix("USDT"),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "/USDT",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.width(6.dp))
                            // Exchange badge
                            val exchLabel = if (currentExchange == ExchangeSource.BINANCE_FUTURES) "PERP" else "DEX"
                            val exchColor = if (currentExchange == ExchangeSource.BINANCE_FUTURES) HeatmapCyan else HeatmapAmber
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(exchColor.copy(alpha = 0.15f))
                                    .border(0.5.dp, exchColor.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = exchLabel,
                                    color = exchColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        // FR + OI row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = String.format("FR: %+.4f%%", fundingRate * 100),
                                color = if (fundingRate >= 0) HeatmapAmber else HeatmapCyan,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = String.format("OI: $%.1fM", openInterestUsd / 1_000_000.0),
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Price + Change
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (currentPrice > 1000)
                                String.format("%,.1f", currentPrice)
                            else
                                String.format("%.4f", currentPrice),
                            color = priceColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = String.format("%+.2f%%", priceChangePct),
                            color = priceColor.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Row 2: Symbol Quick Chips + Exchange toggle + View tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalPanel)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Symbol chips
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(SYMBOL_LIST) { sym ->
                            val isActive = sym == currentSymbol
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isActive) HeatmapCyan.copy(alpha = 0.25f) else Color.Transparent)
                                    .border(
                                        0.5.dp,
                                        if (isActive) HeatmapCyan else TerminalBorder,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onSymbolSelected(sym) }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = sym.removeSuffix("USDT"),
                                    color = if (isActive) HeatmapCyan else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // View mode tabs
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TerminalBackground)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        ModeTab("HM", displayMode == TerminalDisplayMode.BOOKMAP_HEATMAP) {
                            displayMode = TerminalDisplayMode.BOOKMAP_HEATMAP
                        }
                        ModeTab("FP", displayMode == TerminalDisplayMode.SIERRA_FOOTPRINT) {
                            displayMode = TerminalDisplayMode.SIERRA_FOOTPRINT
                        }
                        ModeTab("DOM", displayMode == TerminalDisplayMode.SPLIT_DOM) {
                            displayMode = TerminalDisplayMode.SPLIT_DOM
                        }
                    }
                }
            }
        }

        // ─── MAIN WORKSPACE ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (displayMode) {
                TerminalDisplayMode.BOOKMAP_HEATMAP -> {
                    BookmapHeatmapView(
                        heatmapPixels = heatmapPixels,
                        recentTicks = recentTicks,
                        currentPrice = currentPrice,
                        minPrice = minP,
                        maxPrice = maxP,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                TerminalDisplayMode.SIERRA_FOOTPRINT -> {
                    FootprintChartView(
                        bars = footprintBars,
                        minPrice = minP,
                        maxPrice = maxP,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                TerminalDisplayMode.SPLIT_DOM -> {
                    // Split: Chart left (60%) + DOM right (40%)
                    Row(modifier = Modifier.fillMaxSize()) {
                        BookmapHeatmapView(
                            heatmapPixels = heatmapPixels,
                            recentTicks = recentTicks,
                            currentPrice = currentPrice,
                            minPrice = minP,
                            maxPrice = maxP,
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight()
                        )
                        // Divider
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(TerminalBorder)
                        )
                        DOMLadderView(
                            bids = bids,
                            asks = asks,
                            imbalanceRatio = domImbalance,
                            currentPrice = currentPrice,
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }

        // ─── BOTTOM TICKER: CVD + Liquidation Stream ────────────────────
        Surface(
            color = TerminalPanel,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (liquidations.isNotEmpty()) {
                    Text(
                        text = "💥",
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(liquidations.take(15)) { liq ->
                            val sideColor = if (liq.side == TradeSide.BUY) ElectricEmerald else VibrantCrimson
                            val sideName = if (liq.side == TradeSide.BUY) "SHORT" else "LONG"
                            Text(
                                text = "$sideName REKT $${String.format("%.0f", liq.totalUsd)}",
                                color = sideColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    Text(
                        text = "● LIVE  ",
                        color = ElectricEmerald,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Awaiting liquidation data...",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTab(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (isSelected) HeatmapCyan.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) HeatmapCyan else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace
        )
    }
}
