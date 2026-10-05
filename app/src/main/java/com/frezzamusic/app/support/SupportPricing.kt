package com.frezzamusic.app.support

object SupportPricing {
    const val TRACK_PRICE_BRL: Double = 3.90

    fun albumPrice(trackCount: Int): Double = when {
        trackCount <= 0 -> 0.0
        trackCount <= 8 -> 19.90
        trackCount <= 12 -> 24.90
        trackCount <= 16 -> 29.90
        else -> 34.90
    }

    fun formatBrl(value: Double): String = "R$ %.2f".format(value).replace('.', ',')
}
