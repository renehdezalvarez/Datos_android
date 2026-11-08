package com.example.domain

object UssdParser {

    data class ParsedBalance(
        val valueMB: Double,
        val originalValue: Double,
        val detectedUnit: String,
        val matchedText: String
    )

    /**
     * Extracts mobile data balance in MB from either a raw number (e.g. "3.5")
     * or a carrier USSD response string (e.g. "Su saldo de datos es de 4.25 GB válidos hasta...",
     * "Paquete activo: le quedan 3500 MB").
     */
    fun parse(text: String, defaultUnit: String = "GB"): ParsedBalance? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // Pattern: looks for number followed by GB, MB, Giga, Mega, etc.
        val regex = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(GB|MB|GIGAS?|MEGAS?)\b""")
        val match = regex.find(trimmed)
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
}
