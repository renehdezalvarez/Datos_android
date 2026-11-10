package com.example.domain

/**
 * Single entry point for parsing mobile data quantities from USSD responses,
 * SMS texts or manual user input.
 */
object UssdParser {

    data class ParsedBalance(
        val valueMB: Double,
        val originalValue: Double,
        val detectedUnit: String,
        val matchedText: String
    )

    // Number followed by a data unit: GB, MB, Giga(s), Mega(s), GiB, MiB.
    private val quantityPattern = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(GIB|GB|GIGAS?|MIB|MB|MEGAS?)\b""")

    /**
     * Extracts mobile data balance in MB from either a raw number (e.g. "3.5")
     * or a carrier USSD response string (e.g. "Su saldo de datos es de 4.25 GB válidos hasta...",
     * "Paquete activo: le quedan 3500 MB").
     * Falls back to interpreting the whole text as a number expressed in [defaultUnit].
     */
    fun parse(text: String, defaultUnit: String = "GB"): ParsedBalance? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val match = quantityPattern.find(trimmed)
        if (match != null) {
            val numStr = match.groupValues[1].replace(',', '.')
            val rawUnit = match.groupValues[2].uppercase()
            val num = numStr.toDoubleOrNull() ?: return null
            val isGb = rawUnit.startsWith("G")
            val mb = if (isGb) num * 1024.0 else num
            return ParsedBalance(
                valueMB = mb,
                originalValue = num,
                detectedUnit = if (isGb) "GB" else "MB",
                matchedText = match.value
            )
        }

        // Direct number input fallback (e.g. user typed "3.2" or "3200")
        val directNum = trimmed.replace(',', '.').toDoubleOrNull()
        if (directNum != null && directNum >= 0.0) {
            val isGb = defaultUnit.equals("GB", ignoreCase = true)
            val mb = if (isGb) directNum * 1024.0 else directNum
            return ParsedBalance(
                valueMB = mb,
                originalValue = directNum,
                detectedUnit = if (isGb) "GB" else "MB",
                matchedText = "$directNum $defaultUnit"
            )
        }

        return null
    }

    /**
     * Parses a text message from a USSD response or SMS to detect data quantities
     * (GB or MB). Returns extracted Megabytes or null if no valid balance is detected.
     * When both units appear in the text, GB quantities take precedence (they usually
     * carry the headline balance in carrier messages).
     */
    fun parseDataBalanceMB(text: String): Double? {
        if (text.isBlank()) return null

        val normalized = text.replace(',', '.')
        val gbMatch = Regex("""(?i)(\d+(?:\.\d+)?)\s*(?:GB|GIGAS?|GIB)\b""").find(normalized)
        if (gbMatch != null) {
            val num = gbMatch.groupValues[1].toDoubleOrNull()
            if (num != null && num > 0.0) {
                return num * 1024.0
            }
        }

        val mbMatch = Regex("""(?i)(\d+(?:\.\d+)?)\s*(?:MB|MEGAS?|MIB)\b""").find(normalized)
        if (mbMatch != null) {
            val num = mbMatch.groupValues[1].toDoubleOrNull()
            if (num != null && num > 0.0) {
                return num
            }
        }

        return null
    }
}
