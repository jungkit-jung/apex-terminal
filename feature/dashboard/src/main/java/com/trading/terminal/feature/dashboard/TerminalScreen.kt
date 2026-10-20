package com.trading.terminal.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

    // Dynamic price bounds
    val minP = (bids.lastOrNull()?.price ?: (currentPrice * 0.99)).coerceAtMost(currentPrice * 0.99)
    val maxP = (asks.lastOrNull()?.price ?: (currentPrice * 1.01)).coerceAtLeast(currentPrice * 1.01)

    // Calculate DOM Imbalance
    val totalBidQty = bids.take(20).sumOf { it.quantity }
    val totalAskQty = asks.take(20).sumOf { it.quantity }
    val domImbalance = if (totalBidQty + totalAskQty > 0) {
        (totalBidQty - totalAskQty) / (totalBidQty + totalAskQty)
    } else 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
    ) {
        // 1. TOP HEADER BAR (Symbol, Exchange, Price, Funding, Mode Toggle)
        Surface(
            color = TerminalSurface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Symbol & Exchange Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentSymbol,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (currentExchange == ExchangeSource.BINANCE_FUTURES) "BINANCE PERP" else "HYPERLIQUID DEX",
                        color = HeatmapCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Price & 24h Change
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format("%.1f", currentPrice),
                        color = if (priceChangePct >= 0) ElectricEmerald else VibrantCrimson,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format("FR: %+.4f%% | OI: $%.1fM", fundingRate * 100, openInterestUsd / 1_000_000.0),
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Mode Tabs
                Row(
                    modifier = Modifier
                        .background(TerminalPanel, MaterialTheme.shapes.small)
                        .padding(2.dp)
                ) {
                    TabButton("Heatmap", displayMode == TerminalDisplayMode.BOOKMAP_HEATMAP) {
                        displayMode = TerminalDisplayMode.BOOKMAP_HEATMAP
                    }
                    TabButton("Footprint", displayMode == TerminalDisplayMode.SIERRA_FOOTPRINT) {
                        displayMode = TerminalDisplayMode.SIERRA_FOOTPRINT
                    }
                    TabButton("Split DOM", displayMode == TerminalDisplayMode.SPLIT_DOM) {
                        displayMode = TerminalDisplayMode.SPLIT_DOM
                    }
                }
            }
        }

        // 2. MAIN WORKSPACE VIEW (Heatmap, Footprint, DOM)
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
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left: Heatmap Canvas
                        BookmapHeatmapView(
                            heatmapPixels = heatmapPixels,
                            recentTicks = recentTicks,
                            currentPrice = currentPrice,
                            minPrice = minP,
                            maxPrice = maxP,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        // Right: DOM Ladder (Depth of Market)
                        DOMLadderView(
                            bids = bids,
                            asks = asks,
                            imbalanceRatio = domImbalance,
                            currentPrice = currentPrice,
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                }
            }
        }

        // 3. BOTTOM TICKER: Liquidations & Time & Sales Stream
        Surface(
            color = TerminalPanel,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIQS:",
                    color = HeatmapAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(liquidations.take(15)) { liq ->
                        val sideColor = if (liq.side == TradeSide.BUY) ElectricEmerald else VibrantCrimson
                        val sideName = if (liq.side == TradeSide.BUY) "SHORT REKT" else "LONG REKT"
                        Text(
                            text = "$sideName $${String.format("%.0f", liq.totalUsd)} @ ${liq.price}",
                            color = sideColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (isSelected) TerminalBorder else Color.Transparent, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
