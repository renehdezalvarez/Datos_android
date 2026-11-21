package com.example.domain

/**
 * Parses the interactive package menu returned by the carrier and its final
 * confirmation message.
 *
 * Carrier wording changes often, so every option keeps its original line: if
 * a value cannot be extracted, the dialog shows the raw text and lets the user
 * type the option number manually instead of guessing.
 */
object UssdPurchaseParser {

    data class PackageOption(
        val number: Int,
        val sizeGB: Double?,
        val days: Int?,
        val price: String?,
        val rawLine: String
    )

    data class PurchaseConfirmation(
        val success: Boolean,
        val message: String,
        val needsConfirmation: Boolean = false,
        val failure: Boolean = false,
        val sizeGB: Double? = null,
        val days: Int? = null,
        val price: String? = null
    )

    private val optionWithDelimiter = Regex("""^\s*(\d{1,2})\s*[).:\-]\s*(.+)""")
    private val optionWithSpace = Regex("""^\s*(\d{1,2})\s+(.+)""")
    private val gbPattern = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(?:GB|GIB|GIGAS?)\b""")
    private val mbPattern = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(?:MB|MIB|MEGAS?)\b""")
    private val daysPattern = Regex("""(?i)(\d{1,3})\s*d[ií]as?\b""")
    private val pricePattern = Regex(
        """(?:(?:\\$|CUP|MN)\s*([\d.,]+)|([\d.,]+)\s*(?:\\$|CUP|MN|pesos?)|(?:x|precio)\s*([\d.,]+))"""
    )

    private val successWords = listOf(
        "compra exitosa", "compra realizada", "compra confirmada",
        "paquete activado", "activado correctamente", "se activo",
        "operacion exitosa", "exito", "confirmada", "recarga exitosa"
    )
    private val failureWords = listOf(
        "saldo insuficiente", "no se pudo", "error", "fallo",
        "cancelad", "intente", "no disponible", "denegad"
    )
    private val promptWords = listOf(
        "marque 1 para confirmar", "marque 1 para aceptar",
        "presione 1 para confirmar", "desea comprar", "confirme la compra",
        "para confirmar"
    )
    private val smsSuccessWords = listOf(
        "se ha comprado", "se desconto", "se activa", "plan nacional",
        "compra confirmada", "paquete activado"
    )
    private val smsContextWords = listOf(
        "paquete", "plan", "compra", "recarga", "etecsa", "desconto"
    )

    /** Extracts every selectable package from the USSD menu text. */
    fun parseMenu(text: String): List<PackageOption> {
        val seen = mutableSetOf<Int>()
        return text.lineSequence().mapNotNull { line ->
            val match = optionWithDelimiter.find(line) ?: optionWithSpace.find(line)
                ?: return@mapNotNull null
            val number = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..99 }
                ?: return@mapNotNull null
            if (!seen.add(number)) return@mapNotNull null

            val body = match.groupValues[2]
            val sizeGB = parseSizeGB(body)
            val days = daysPattern.find(body)?.groupValues?.get(1)?.toIntOrNull()
            val price = parsePrice(body)
            if (sizeGB == null && days == null && price == null) return@mapNotNull null

            PackageOption(
                number = number,
                sizeGB = sizeGB,
                days = days,
                price = price,
                rawLine = line.trim()
            )
        }.toList()
    }

    /**
     * Detects success, an explicit "press 1 to confirm" prompt, or a failure.
     * Failure always wins; a prompt is not treated as a completed purchase.
     */
    fun parseConfirmation(text: String): PurchaseConfirmation {
        val normalized = normalize(text)
        val failed = failureWords.any { normalized.contains(it) }
        val needsConfirmation = !failed && promptWords.any { normalized.contains(it) }
        val succeeded = !failed && !needsConfirmation && successWords.any { normalized.contains(it) }
        return PurchaseConfirmation(
            success = succeeded,
            needsConfirmation = needsConfirmation,
            failure = failed,
            message = text.trim().replace('\n', ' ').take(200),
            sizeGB = parseSizeGB(text),
            days = daysPattern.find(text)?.groupValues?.get(1)?.toIntOrNull(),
            price = parsePrice(text)
        )
    }

    /** Parses the carrier's confirmation SMS, not the intermediate USSD prompt. */
    fun parseSmsConfirmation(text: String): PurchaseConfirmation {
        val base = parseConfirmation(text)
        val normalized = normalize(text)
        val aboutPurchase = smsContextWords.any { normalized.contains(it) }
        val confirmed = !base.failure && aboutPurchase &&
            smsSuccessWords.any { normalized.contains(it) }
        return base.copy(
            success = confirmed,
            needsConfirmation = false,
            failure = base.failure || (aboutPurchase && !confirmed && normalized.contains("no se pudo"))
        )
    }

    private fun parseSizeGB(text: String): Double? {
        gbPattern.find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            ?.let { return it }
        val mb = mbPattern.find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            ?: return null
        return mb / 1024.0
    }

    private fun parsePrice(text: String): String? {
        val match = pricePattern.find(text) ?: return null
        val value = match.groupValues.firstOrNull { it.isNotBlank() } ?: return null
        return value.replace(',', '.')
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace('á', 'a')
        .replace('é', 'e')
        .replace('í', 'i')
        .replace('ó', 'o')
        .replace('ú', 'u')
        .replace('ñ', 'n')
}
