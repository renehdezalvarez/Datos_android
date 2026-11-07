package com.example.domain

import java.util.regex.Pattern

object UssdResponseParser {

    /**
     * Parses a text message from a USSD response or SMS to detect data quantities (GB or MB).
     * Returns extracted Megabytes or null if no valid balance is detected.
     */
    fun parseDataBalanceMB(text: String): Double? {
        if (text.isBlank()) return null

        val clean = text.replace(",", ".")

        // Regex 1: Matches numbers followed by GB / Giga / Gigas
        // e.g. "4.2 GB", "3.5GB", "10 gigas"
        val gbPattern = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:gb|giga|gigas|gib)\\b")
        val gbMatcher = gbPattern.matcher(clean)
        if (gbMatcher.find()) {
            val numStr = gbMatcher.group(1)
            val num = numStr?.toDoubleOrNull()
            if (num != null && num > 0.0) {
                return num * 1024.0
            }
        }

        // Regex 2: Matches numbers followed by MB / Mega / Megas
        // e.g. "3450 MB", "500mb", "1200 megas"
        val mbPattern = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:mb|mega|megas|mib)\\b")
        val mbMatcher = mbPattern.matcher(clean)
        if (mbMatcher.find()) {
            val numStr = mbMatcher.group(1)
            val num = numStr?.toDoubleOrNull()
            if (num != null && num > 0.0) {
                return num
            }
        }

        return null
    }
}
