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

    /** Opens the package menu. */
    const val MENU_CODE = "$BASE_CODE#"

    /** Selects a package without accepting it yet (used by the assisted fallback). */
    fun selectionCode(optionNumber: Int): String = "$BASE_CODE*$optionNumber#"

    /** Selects a package and accepts the purchase with the final `1`. */
    fun confirmationCode(optionNumber: Int): String = "$BASE_CODE*$optionNumber*1#"
}
