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
    // e.g. "le quedan 20 días", "vence en 5 días", "20 días restantes",
    // "validos 14 dias", "vigencia de 14 dias".
    private val anchoredDayPatterns = listOf(
        Regex("""(?:quedan|restan|resta|vence en|vence dentro de|disponibles?|vigencia|v[áa]lid[ao]s?)\s*(?::|de|por)?\s*(\d{1,3})\s*(?:$dayWord)\b"""),
        Regex("""(\d{1,3})\s*(?:$dayWord)\s*(?:restantes?|disponibles?|de validez|de vigencia|v[áa]lid[ao]s?)\b""")
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

    // ------------------------------------------------------------------
    // Prepaid balance-recharge date (*222*732#)
    // ------------------------------------------------------------------

    data class RechargeDateInfo(
        /** Date given by the carrier (last day WITHOUT recharge access). */
        val carrierDateMs: Long,
        /** First day the line can be recharged: carrier date + 1 day. */
        val eligibleDateMs: Long,
        val matchedText: String
    )

    private data class DateCandidate(
        val index: Int,
        val carrierDateMs: Long,
        val text: String
    )

    private val rechargeContextWords = listOf(
        "recarg", "posterior", "desde", "a partir", "puede", "fecha",
        "día", "dia", "habilit", "disponible"
    )

    private val numericDatePattern = Regex("""(\d{1,2})[\/\.\-](\d{1,2})(?:[\/\.\-](\d{2,4}))?""")
    private val textualDatePattern =
        Regex("""(?i)(\d{1,2})\s+de\s+([a-záéíóúñ]+)(?:\s+de\s+(\d{2,4}))?""")

    private val monthNames = mapOf(
        "ene" to Calendar.JANUARY, "enero" to Calendar.JANUARY,
        "jan" to Calendar.JANUARY, "january" to Calendar.JANUARY,
        "feb" to Calendar.FEBRUARY, "febrero" to Calendar.FEBRUARY,
        "february" to Calendar.FEBRUARY,
        "mar" to Calendar.MARCH, "marzo" to Calendar.MARCH, "march" to Calendar.MARCH,
        "abr" to Calendar.APRIL, "abre" to Calendar.APRIL, "abril" to Calendar.APRIL,
        "apr" to Calendar.APRIL, "april" to Calendar.APRIL,
        "may" to Calendar.MAY, "mayo" to Calendar.MAY,
        "jun" to Calendar.JUNE, "junio" to Calendar.JUNE, "june" to Calendar.JUNE,
        "jul" to Calendar.JULY, "julio" to Calendar.JULY, "july" to Calendar.JULY,
        "ago" to Calendar.AUGUST, "agosto" to Calendar.AUGUST,
        "aug" to Calendar.AUGUST, "august" to Calendar.AUGUST,
        "sep" to Calendar.SEPTEMBER, "sept" to Calendar.SEPTEMBER,
        "septiembre" to Calendar.SEPTEMBER, "set" to Calendar.SEPTEMBER,
        "setiembre" to Calendar.SEPTEMBER, "september" to Calendar.SEPTEMBER,
        "oct" to Calendar.OCTOBER, "octubre" to Calendar.OCTOBER, "october" to Calendar.OCTOBER,
        "nov" to Calendar.NOVEMBER, "noviembre" to Calendar.NOVEMBER, "november" to Calendar.NOVEMBER,
        "dic" to Calendar.DECEMBER, "diciembre" to Calendar.DECEMBER,
        "dec" to Calendar.DECEMBER, "december" to Calendar.DECEMBER
    )

    /**
     * Extracts the date from the carrier's *222*732# answer. The operator
     * reports the last day without recharge access ("...posterior al dia
     * 07-12-26"); the returned [RechargeDateInfo.eligibleDateMs] is the NEXT
     * day, which is when the user can actually top up.
     *
     * Supports dd/mm/yy, dd-mm-yyyy, "dd de mes de yyyy" and dates without an
     * explicit year (next occurrence is assumed). Dates near recharge wording
     * win; otherwise the first date in the message is used.
     */
    fun parseRechargeDate(text: String, now: Calendar? = null): RechargeDateInfo? {
        if (text.isBlank()) return null
        val lowerText = text.lowercase()
        // A balance answer can also contain dates ("válido hasta 25/11").
        // Only treat it as a recharge answer when recharge wording is present.
        if (rechargeContextWords.none { lowerText.contains(it) }) return null
        val reference = now ?: Calendar.getInstance()

        val candidates = buildList {
            numericDatePattern.findAll(text).forEach { match ->
                val day = match.groupValues[1].toIntOrNull() ?: return@forEach
                val month = match.groupValues[2].toIntOrNull() ?: return@forEach
                val carrierMs = carrierDateMs(day, month, match.groupValues[3], reference)
                    ?: return@forEach
                add(DateCandidate(match.range.first, carrierMs, match.value))
            }
            textualDatePattern.findAll(text).forEach { match ->
                val day = match.groupValues[1].toIntOrNull() ?: return@forEach
                val month = monthNames[match.groupValues[2].lowercase()] ?: return@forEach
                val carrierMs = carrierDateMs(day, month + 1, match.groupValues[3], reference)
                    ?: return@forEach
                add(DateCandidate(match.range.first, carrierMs, match.value))
            }
        }.sortedBy { it.index }

        val chosen = candidates.firstOrNull { candidate ->
            val contextStart = maxOf(0, candidate.index - 70)
            val context = text.substring(contextStart, candidate.index).lowercase()
            rechargeContextWords.any { context.contains(it) }
        } ?: candidates.firstOrNull() ?: return null

        val eligibleMs = Calendar.getInstance().apply {
            timeInMillis = chosen.carrierDateMs
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        return RechargeDateInfo(chosen.carrierDateMs, eligibleMs, chosen.text)
    }

    private fun carrierDateMs(
        day: Int,
        month: Int,
        yearPart: String,
        reference: Calendar
    ): Long? {
        if (day !in 1..31 || month !in 1..12) return null
        val hasYear = yearPart.isNotEmpty()
        var year = if (hasYear) yearPart.toIntOrNull() ?: return null else reference.get(Calendar.YEAR)
        if (year in 1..99) year += 2000

        val calendar = Calendar.getInstance().apply {
            clear()
            isLenient = false
            set(year, month - 1, day, 0, 0, 0)
        }
        val carrierMs = try {
            calendar.timeInMillis
        } catch (_: IllegalArgumentException) {
            return null
        }

        // Without a year, a date already in the past means next year.
        if (!hasYear) {
            val todayStart = Calendar.getInstance().apply {
                timeInMillis = reference.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (carrierMs < todayStart) {
                calendar.add(Calendar.YEAR, 1)
                return calendar.timeInMillis
            }
        }
        return carrierMs
    }
}
