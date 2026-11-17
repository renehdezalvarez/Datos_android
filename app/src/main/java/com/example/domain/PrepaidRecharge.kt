package com.example.domain

/**
 * Prepaid-line-only rules for the balance-recharge date.
 *
 * The carrier answers *222*732# with a date (e.g. "Puede recargar posterior
 * al dia 07-12-26"). That date is the last day WITHOUT recharge access; the
 * next one is when the line can actually be topped up.
 */
object PrepaidRecharge {

    const val USSD_CODE = "*222*732#"

    /** True when this prepaid cycle has not been queried yet. */
    fun needsQuery(
        planType: String,
        checkedCycleStartMs: Long?,
        purchaseTimestamp: Long
    ): Boolean = planType == "PREPAID_35" && checkedCycleStartMs != purchaseTimestamp
}
