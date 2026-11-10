package com.example

import com.example.domain.UssdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
