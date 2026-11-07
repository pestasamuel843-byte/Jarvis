package com.jarvis.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Live weather for Svit using the public Open-Meteo forecast API.
 * No API key is required. A failed request is reported instead of fabricating conditions.
 */
object LiveWeather {
    fun forSvit(): String {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=49.060&longitude=20.206&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=1&timezone=Europe%2FBratislava")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "JARVIS-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Služba počasia odpovedala kódom HTTP ${connection.responseCode}.")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val code = current.optInt("weather_code", -1)
            val condition = when (code) {
                0 -> "jasno"
                1 -> "prevažne jasno"
                2 -> "polooblačno"
                3 -> "zamračené"
                45, 48 -> "hmla"
                51, 53, 55 -> "mrholenie"
                56, 57 -> "mrznúce mrholenie"
                61, 63, 65 -> "dážď"
                66, 67 -> "mrznúci dážď"
                71, 73, 75, 77 -> "sneženie"
                80, 81, 82 -> "prehánky"
                85, 86 -> "snehové prehánky"
                izzle -> "búrka"
                else -> "stav sa nepodarilo určiť"
            }
            val temp = current.optDouble("temperature_2m", Double.NaN)
            val feels = current.optDouble("apparent_temperature", Double.NaN)
            val humidity = current.optInt("relative_humidity_2m", -1)
            val wind = current.optDouble("wind_speed_10m", Double.NaN)
            val max = daily.getJSONArray("temperature_2m_max").optDouble(0, Double.NaN)
            val min = daily.getJSONArray("temperature_2m_min").optDouble(0, Double.NaN)
            val rain = daily.getJSONArray("precipitation_probability_max").optInt(0, -1)
            fun number(value: Double) = if (value.isNaN()) "údaj nedostupný" else String.format(Locale("sk", "SK"), "%.1f", value)
            return buildString {
                append("Aktuálne počasie v Svite podľa živých dát Open-Meteo: ")
                append(number(temp)).append(" °C, ").append(condition).append(". ")
                append("Pocitová teplota ").append(number(feels)).append(" °C. ")
                append("Vlhkosť ").append(if (humidity >= 0) "$humidity %" else "údaj nedostupný").append(", ")
                append("vietor ").append(number(wind)).append(" km/h. ")
                append("Dnes: minimum ").append(number(min)).append(" °C, maximum ").append(number(max)).append(" °C")
                if (rain >= 0) append(", pravdepodobnosť zrážok do ").append(rain).append(" %")
                append(". Údaje sa môžu meniť.")
            }
        } finally {
            connection.disconnect()
        }
    }
}
