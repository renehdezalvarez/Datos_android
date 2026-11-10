package com.example

import com.example.domain.UssdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class UssdParserTest {

    // ------------------------------------------------------------------
    // parseDataBalanceMB: texto crudo de operador (respuesta USSD / SMS)
    // ------------------------------------------------------------------

    @Test
    fun testParseDataBalanceMb_gbFormats() {
        // "4.5 GB" -> 4.5 * 1024 = 4608 MB
        val res1 = UssdParser.parseDataBalanceMB("Tu saldo es de 4.5 GB hasta el 25/11")
        assertEquals(4608.0, res1!!, 0.001)

        // Comma decimal "2,5GB"
        val res2 = UssdParser.parseDataBalanceMB("Paquete activo con 2,5GB disponibles")
        assertEquals(2560.0, res2!!, 0.001)

        // Word "gigas"
        val res3 = UssdParser.parseDataBalanceMB("Te restan 10 gigas de navegacion")
        assertEquals(10240.0, res3!!, 0.001)
    }

    @Test
    fun testParseDataBalanceMb_mbFormats() {
        val res1 = UssdParser.parseDataBalanceMB("Saldo restante: 3200 MB.")
        assertEquals(3200.0, res1!!, 0.001)

        val res2 = UssdParser.parseDataBalanceMB("Consumo actual: quedan 850 megas")
        assertEquals(850.0, res2!!, 0.001)
    }

    @Test
    fun testParseDataBalanceMb_prefersGbWhenBothUnitsAppear() {
        val res = UssdParser.parseDataBalanceMB("Consumiste 500 MB, le quedan 1.5 GB")
        assertEquals(1536.0, res!!, 0.001)
    }

    @Test
    fun testParseDataBalanceMb_emptyOrInvalid() {
        assertNull(UssdParser.parseDataBalanceMB(""))
        assertNull(UssdParser.parseDataBalanceMB("No tienes ningun paquete activo."))
    }

    // ------------------------------------------------------------------
    // parse: entrada manual del usuario (número simple o texto)
    // ------------------------------------------------------------------

    @Test
    fun testParse_manualNumberWithGbUnit() {
        val res = UssdParser.parse("4.5", "GB")
        assertEquals(4608.0, res!!.valueMB, 0.001)
        assertEquals("GB", res.detectedUnit)
    }

    @Test
    fun testParse_manualNumberWithMbUnit() {
        val res = UssdParser.parse("2500", "MB")
        assertEquals(2500.0, res!!.valueMB, 0.001)
        assertEquals("MB", res.detectedUnit)
    }

    @Test
    fun testParse_carrierTextWithUnits() {
        val res = UssdParser.parse("Su saldo de datos es de 4.25 GB válidos hasta el 30/11", "GB")
        assertEquals(4352.0, res!!.valueMB, 0.001)
    }

    @Test
    fun testParse_commaDecimalManualInput() {
        val res = UssdParser.parse("3,5", "GB")
        assertEquals(3584.0, res!!.valueMB, 0.001)
    }

    @Test
    fun testParse_invalidInput() {
        assertNull(UssdParser.parse("", "GB"))
        assertNull(UssdParser.parse("   ", "GB"))
        assertNull(UssdParser.parse("abc", "GB"))
    }

    // ------------------------------------------------------------------
    // parseDaysRemaining: días restantes del paquete prepago
    // ------------------------------------------------------------------

    private fun dateOf(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply { set(year, month, day, 12, 0, 0) }

    @Test
    fun testParseDaysRemaining_explicitCounts() {
        assertEquals(20, UssdParser.parseDaysRemaining("le quedan 2.5 GB y 20 días"))
        assertEquals(20, UssdParser.parseDaysRemaining("Su saldo vence en 20 días"))
        assertEquals(20, UssdParser.parseDaysRemaining("20 DIAS restantes"))
        assertEquals(5, UssdParser.parseDaysRemaining("quedan 5 dia"))
    }

    @Test
    fun testParseDaysRemaining_validosPhrasing() {
        // Real carrier wording: "validos 14 dias" (today included)
        assertEquals(14, UssdParser.parseDaysRemaining("Datos: 2048 MB validos 14 dias"))
        assertEquals(14, UssdParser.parseDaysRemaining("VALIDOS 14 DIAS"))
        assertEquals(14, UssdParser.parseDaysRemaining("validos 14 dias"))
        assertEquals(14, UssdParser.parseDaysRemaining("bono valido por 14 dias"))
        assertEquals(14, UssdParser.parseDaysRemaining("14 dias validos"))
        assertEquals(1, UssdParser.parseDaysRemaining("datos validos 1 dia"))
        // "validos hasta <date>" must still fall through to the date branch
        val now = dateOf(2026, Calendar.NOVEMBER, 10)
        assertEquals(16, UssdParser.parseDaysRemaining("2.5 GB válidos hasta 25/11", now))
    }

    @Test
    fun testParseDaysRemaining_prefersRemainingOverTotal() {
        // "paquete DE 35 días" (total) + "quedan 20 días" (remaining) -> 20
        assertEquals(20, UssdParser.parseDaysRemaining("Paquete de 35 días, le quedan 20 días"))
        // Without a remaining-phrasing, the smallest count is the safest guess
        assertEquals(20, UssdParser.parseDaysRemaining("Su paquete de 35 días tiene 20 días"))
    }

    @Test
    fun testParseDaysRemaining_expirationDates() {
        val now = dateOf(2026, Calendar.NOVEMBER, 10)
        // 10 -> 25 Nov inclusive = 16 days
        assertEquals(16, UssdParser.parseDaysRemaining("2.5 GB válidos hasta 25/11", now))
        assertEquals(16, UssdParser.parseDaysRemaining("válido hasta el 25/11/2026", now))
        // Month rollover into December
        assertEquals(24, UssdParser.parseDaysRemaining("válido hasta 03/12", now))
        // Same day = expires tonight
        assertEquals(1, UssdParser.parseDaysRemaining("válido hasta 10/11", now))
    }

    @Test
    fun testParseDaysRemaining_yearWrapAround() {
        val now = dateOf(2026, Calendar.DECEMBER, 28)
        // 05/01 without year must be interpreted as January NEXT year
        assertEquals(9, UssdParser.parseDaysRemaining("vigente hasta 05/01", now))
    }

    @Test
    fun testParseDaysRemaining_invalid() {
        assertNull(UssdParser.parseDaysRemaining("No tiene paquetes activos"))
        assertNull(UssdParser.parseDaysRemaining(""))
        assertNull(UssdParser.parseDaysRemaining("Su saldo principal es 25.00 CUP"))
    }
}
