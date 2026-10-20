# 🚀 ApexTerminal — Standalone Native Android Trading Terminal

> **Inspirasi: Bookmap & Sierra Chart**  
> **100% Free Public Market Data · On-Device Analysis · Auto-Build via GitHub Actions**

---

## 🌟 Fitur Utama

- **Bookmap-Style Liquidity Heatmap**:
  - Background thermal visualizer order book historis (Logarithmic normalization: $\frac{\ln(1+V)}{\ln(1+V_{\max})}$).
  - Skala warna: Biru Tua $\to$ Cyan (Bid Wall) $\to$ Amber $\to$ Oranye/Kuning (Ask Wall) $\to$ Hot White.
  - **Trade Bubbles**: Gelembung market execution dengan radius proporsional terhadap volume $\sqrt{Q}$ (Hijau = Taker Buy, Merah = Taker Sell).
- **Sierra Chart-Style Footprint (Number Bars)**:
  - Cluster bid x ask volume di tiap tick harga.
  - Deteksi **POC (Point of Control)** otomatis.
  - Diagonal Imbalance detection ($\ge 300\%$).
- **DOM (Depth of Market) Ladder Level 2**:
  - Real-time bid/ask order book 30 level.
  - Imbalance meter bar.
  - Center spread indicator.
- **CVD (Cumulative Volume Delta)**:
  - Deteksi divergensi penyerapan (Bullish/Bearish Absorption).
- **Sumber Data 100% Free (No API Key Required)**:
  - **Binance USD-M Futures**: `aggTrade` (tick), `depth@100ms` (L2), `forceOrder` (liquidasi), `premiumIndex` (funding rate), `openInterest`.
  - **Hyperliquid DEX**: `l2Book`, `trades` via WebSocket.
- **Arsitektur Standalone**:
  - Seluruh komputasi berjalan di Android (on-device). Tidak butuh backend server tambahan.

---

## 🛠️ Modul Project

```
trading-terminal/
├── app/                  # Main Android Application & Native C++ SIMD mapper
├── core/
│   ├── data/             # Binance WS, Hyperliquid WS, REST Client, Room DB
│   ├── engine/           # Footprint, OrderBook, Heatmap 2D matrix, CVD
│   └── ui/               # Bookmap/Sierra Dark Theme, Design Tokens
├── feature/
│   ├── chart/            # Heatmap Canvas, Footprint Chart, Trade Bubbles
│   ├── dom/              # Real-time Level 2 Ladder
│   ├── dashboard/        # Multi-pane Terminal Screen & Ticker
│   └── watchlist/        # Market selection
└── .github/workflows/    # CI/CD: Auto-build signed & debug APK
```

---

## 📦 Cara Build via GitHub Actions

1. Buat repository baru di GitHub (misal: `apex-terminal`).
2. Push repository lokal ini ke GitHub:
   ```bash
   cd ~/trading-terminal
   git add .
   git commit -m "feat: initial commit apex trading terminal"
   git remote add origin https://github.com/USERNAME/REPO_NAME.git
   git push -u origin main
   ```
3. Buka tab **Actions** di GitHub.
4. Workflow **Build Android Trading Terminal** akan berjalan otomatis dan menghasilkan:
   - **`ApexTerminal-Debug-APK`** (bisa langsung di-download dan di-install di HP)
   - **`ApexTerminal-Release-APK`** (signed)
5. Untuk membuat rilis resmi, cukup push tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   GitHub Actions akan otomatis membuatkan halaman GitHub Release dengan APK terlampir!
