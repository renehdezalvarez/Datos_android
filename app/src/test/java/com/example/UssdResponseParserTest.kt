package com.example

import com.example.domain.UssdResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UssdResponseParserTest {

    @Test
    fun testParseGb_standardFormats() {
        // "4.5 GB" -> 4.5 * 1024 = 4608 MB
        val res1 = UssdResponseParser.parseDataBalanceMB("Tu saldo es de 4.5 GB hasta el 25/11")
        assertEquals(4608.0, res1!!, 0.001)

        // Comma decimal "2,5GB"
        val res2 = UssdResponseParser.parseDataBalanceMB("Paquete activo con 2,5GB disponibles")
        assertEquals(2560.0, res2!!, 0.001)

        // Word "gigas"
        val res3 = UssdResponseParser.parseDataBalanceMB("Te restan 10 gigas de navegacion")
        assertEquals(10240.0, res3!!, 0.001)
    }

    @Test
    fun testParseMb_standardFormats() {
        val res1 = UssdResponseParser.parseDataBalanceMB("Saldo restante: 3200 MB.")
        assertEquals(3200.0, res1!!, 0.001)

        val res2 = UssdResponseParser.parseDataBalanceMB("Consumo actual: quedan 850 megas")
        assertEquals(850.0, res2!!, 0.001)
    }

    @Test
    fun testParse_emptyOrInvalid() {
        assertNull(UssdResponseParser.parseDataBalanceMB(""))
        assertNull(UssdResponseParser.parseDataBalanceMB("No tienes ningun paquete activo."))
    }
}
