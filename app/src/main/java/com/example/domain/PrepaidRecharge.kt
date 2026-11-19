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

    /**
     * Ajustar el día o recibir "quedan N días" reancla el paquete, pero no es
     * una compra nueva: movemos la marca de "ya consultado" con el ancla para
     * no volver a pedir la fecha de recarga. Null sigue significando "nunca
     * se pidió" y, por tanto, debe consultarse una vez.
     */
    fun reanchoredCheckedCycleStart(
        checkedCycleStartMs: Long?,
        newPurchaseTimestamp: Long
    ): Long? = checkedCycleStartMs?.let { newPurchaseTimestamp }
}
