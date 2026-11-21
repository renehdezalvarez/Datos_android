package com.example.domain

/**
 * USSD codes for buying a new prepaid package on line 2.
 *
 * The carrier accepts the whole selection in one request, so the app can
 * build the final code instead of trying to type digits into an open USSD
 * session (Android does not expose a reliable API for that).
 */
object PrepaidPurchase {
    private const val BASE_CODE = "*133*1*4"

    /**
     * The carrier's package menu, kept in the app so no USSD consultation is
     * needed just to show the options. Only the final purchase code is dialed.
     */
    val MENU_TEXT = """
        1 - 4.5GB x 240.00
        2 - 2GB +15min +20SMS x 120.00
        3 - 4GB +35min +40SMS x 240.00
        4 - 6GB +60min +70SMS x 360.00
    """.trimIndent()

    /** Parsed menu with the prepaid line's default validity of 35 days. */
    val MENU_OPTIONS = UssdPurchaseParser.parseMenu(MENU_TEXT).map { it.copy(days = 35) }

    /** Selects a package and accepts the purchase with the final `1`. */
    fun confirmationCode(optionNumber: Int): String = "$BASE_CODE*$optionNumber*1#"
}
