package com.trading.terminal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class TradingApplication : Application() {

    companion object {
        const val CHANNEL_ID = "trading_terminal_service"
        init {
            try {
                System.loadLibrary("tradingterminal")
            } catch (e: UnsatisfiedLinkError) {
                // Fallback to pure Kotlin compute if native library is omitted
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Market Data Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps low-latency tick stream active in background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
