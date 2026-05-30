// app/src/main/java/com/example/pulserds_prog7313/utils/CurrencyManager.kt
package com.example.pulserds_prog7313.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object CurrencyManager {

    private const val PREFS_SETTINGS   = "app_settings"
    private const val KEY_CURRENCY_IDX = "currency_index"
    private const val KEY_CACHED_RATE  = "cached_exchange_rate"
    private const val KEY_CACHE_TIME   = "cached_exchange_time"
    private const val CACHE_TTL_MS     = 3_600_000L  // 1 hour

    // Mirrors the order of the array in showCurrencyDialog()
    val CURRENCIES = listOf(
        Currency("ZAR", "R",  "South African Rand"),
        Currency("USD", "$",  "US Dollar"),
        Currency("EUR", "€",  "Euro"),
        Currency("GBP", "£",  "British Pound"),
        Currency("BWP", "P",  "Botswana Pula")
    )

    data class Currency(val code: String, val symbol: String, val label: String)

    fun getSelectedCurrency(context: Context): Currency {
        val idx = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getInt(KEY_CURRENCY_IDX, 0)
        return CURRENCIES.getOrElse(idx) { CURRENCIES[0] }
    }

    /**
     * Returns the cached exchange rate (ZAR → selected currency).
     * Falls back to 1.0 (i.e. no conversion) if ZAR is selected or cache is empty.
     */
    fun getCachedRate(context: Context): Double {
        val selected = getSelectedCurrency(context)
        if (selected.code == "ZAR") return 1.0
        val prefs    = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_CACHED_RATE, 1f).toDouble()
    }

    /**
     * Converts a ZAR amount to the display currency using the cached rate.
     */
    fun convert(context: Context, zarAmount: Double): Double =
        zarAmount * getCachedRate(context)

    /**
     * Formats a ZAR amount as a display string in the selected currency.
     */
    fun format(context: Context, zarAmount: Double): String {
        val currency     = getSelectedCurrency(context)
        val converted    = convert(context, zarAmount)
        return "${currency.symbol} %,.2f".format(converted)
    }


    fun formatRounded(context: Context, zarAmount: Double): String {
        val currency  = getSelectedCurrency(context)
        val converted = convert(context, zarAmount)
        return "${currency.symbol} %,.0f".format(converted)
    }

    /**
     * Fetches a fresh exchange rate from the Open Exchange Rates API (free tier).
     * Caches the result for [CACHE_TTL_MS]. Call this once on app start or
     * when the user changes their currency in Settings.
     *
     * Replace APP_ID with your free key from https://openexchangerates.org
     */
    suspend fun refreshRateIfNeeded(context: Context) {
        val selected = getSelectedCurrency(context)
        if (selected.code == "ZAR") return     // no conversion needed

        val prefs     = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_CACHE_TIME, 0L)
        if (System.currentTimeMillis() - lastFetch < CACHE_TTL_MS) return  // still fresh

        withContext(Dispatchers.IO) {
            try {
                // Free tier: base is always USD, so fetch ZAR and target separately
                val APP_ID = "13e060ea9fbb45bead0b53ec06a9b262"
                val url    = "https://openexchangerates.org/api/latest.json?app_id=$APP_ID&symbols=ZAR,${selected.code}"
                val json   = JSONObject(URL(url).readText())
                val rates  = json.getJSONObject("rates")
                val zarRate    = rates.getDouble("ZAR")    // USD → ZAR
                val targetRate = rates.getDouble(selected.code) // USD → target
                // ZAR → target = (USD→target) / (USD→ZAR)
                val zarToTarget = targetRate / zarRate
                prefs.edit()
                    .putFloat(KEY_CACHED_RATE, zarToTarget.toFloat())
                    .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) {
                // Network failed — keep using the cached rate (or 1.0 fallback)
            }
        }
    }
}