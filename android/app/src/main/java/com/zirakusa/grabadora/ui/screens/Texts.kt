package com.zirakusa.grabadora.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The phone's language. */
@Composable fun locale(): Locale = LocalConfiguration.current.locales[0]

/** «21 nov 2026 6:36» in the phone's language (no dots after the abbreviations). */
fun dateText(millis: Long, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofPattern("d MMM yyyy H:mm", locale).format(Instant.ofEpochMilli(millis).atZone(zone)).replace(".", "")

/** «21/11/2026 06:36:40» for «Detalles». */
fun fullDateText(millis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").format(Instant.ofEpochMilli(millis).atZone(zone))

/** «1,5×», «0,75×» with the phone's decimal mark. */
fun speedText(speed: Float, locale: Locale): String =
    (if (speed * 100 % 10 == 0f) String.format(locale, "%.1f", speed) else String.format(locale, "%.2f", speed)) + "×"
