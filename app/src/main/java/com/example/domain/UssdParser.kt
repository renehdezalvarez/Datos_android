package com.example.domain

import java.util.Calendar

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

    private const val DAY_MS = 24L * 60L * 60L * 1000L

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

    // ------------------------------------------------------------------
    // Remaining days of the package (prepaid lines)
    // ------------------------------------------------------------------

    private val dayWord = """d[ií]as?"""

    // Phrasings where the number clearly refers to the days LEFT,
    // e.g. "le quedan 20 días", "vence en 5 días", "20 días restantes".
    private val anchoredDayPatterns = listOf(
        Regex("""(?:quedan|restan|resta|vence en|vence dentro de|disponibles?|vigencia)\s*:?\s*(\d{1,3})\s*(?:$dayWord)\b"""),
        Regex("""(\d{1,3})\s*(?:$dayWord)\s*(?:restantes?|disponibles?|de validez|de vigencia)\b""")
    )

    // Expiration date, e.g. "válido hasta 25/11", "vence el 03/12/2026".
    private val expirationDatePattern =
        Regex("""(?:hasta|vence|expira|vigente)\s*(?:el\s*)?(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2,4}))?""")

    /**
     * Extracts how many days the prepaid package has LEFT from a carrier message.
     * Understands explicit counts ("le quedan 20 días") and expiration dates
     * ("válido hasta 25/11" — counted from [now], both inclusive).
     *
     * When several "N días" appear (e.g. "paquete de 35 días, le quedan 20"),
     * phrasings that clearly mean "remaining" win; otherwise the smallest
     * count is used (remaining can never exceed the package duration).
     */
    fun parseDaysRemaining(text: String, now: Calendar? = null): Int? {
        if (text.isBlank()) return null
        val lower = text.lowercase()

        for (pattern in anchoredDayPatterns) {
            pattern.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }

        val allCounts = Regex("""(\d{1,3})\s*(?:$dayWord)\b""")
            .findAll(lower)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
        if (allCounts.isNotEmpty()) return allCounts.min()

        val dateMatch = expirationDatePattern.find(lower) ?: return null
        val day = dateMatch.groupValues[1].toIntOrNull() ?: return null
        val month = dateMatch.groupValues[2].toIntOrNull() ?: return null
        if (day !in 1..31 || month !in 1..12) return null

        val reference = now ?: Calendar.getInstance()
        var year = reference.get(Calendar.YEAR)
        val hasExplicitYear = dateMatch.groupValues[3].isNotEmpty()
        if (hasExplicitYear) {
            year = dateMatch.groupValues[3].toInt()
            if (year < 100) year += 2000
        }

        val target = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 23, 59, 59)
        }
        // Without an explicit year, a date already in the past means next year.
        if (!hasExplicitYear && target.timeInMillis < reference.timeInMillis) {
            target.add(Calendar.YEAR, 1)
        }

        val diffMs = target.timeInMillis - reference.timeInMillis
        val days = (diffMs / DAY_MS).toInt() + 1 // count today as a remaining day
        return days.coerceAtLeast(0)
    }
}
