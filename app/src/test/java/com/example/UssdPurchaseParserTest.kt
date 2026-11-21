package com.example

import com.example.domain.PrepaidPurchase
import com.example.domain.UssdPurchaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdPurchaseParserTest {

    @Test
    fun `builds the final USSD code for the selected package`() {
        assertEquals("*133*1*4*2*1#", PrepaidPurchase.confirmationCode(2))
    }

    @Test
    fun `in-app menu contains all four carrier packages`() {
        val options = PrepaidPurchase.MENU_OPTIONS

        assertEquals(listOf(1, 2, 3, 4), options.map { it.number })
        assertEquals(4.5, options[0].sizeGB!!, 0.001)
        assertEquals("240.00", options[0].price)
        assertEquals(35, options[0].days)
        assertEquals("1 - 4.5GB x 240.00", options[0].rawLine)

        assertEquals(2.0, options[1].sizeGB!!, 0.001)
        assertEquals("120.00", options[1].price)
        assertEquals(35, options[1].days)
        assertEquals("2 - 2GB +15min +20SMS x 120.00", options[1].rawLine)

        assertEquals(4.0, options[2].sizeGB!!, 0.001)
        assertEquals("240.00", options[2].price)
        assertEquals(35, options[2].days)
        assertEquals("3 - 4GB +35min +40SMS x 240.00", options[2].rawLine)

        assertEquals(6.0, options[3].sizeGB!!, 0.001)
        assertEquals("360.00", options[3].price)
        assertEquals(35, options[3].days)
        assertEquals("4 - 6GB +60min +70SMS x 360.00", options[3].rawLine)
    }

    @Test
    fun `parses numbered packages with size days and price`() {
        val menu = """
            Paquetes disponibles:
            1) 2 GB por 7 dias - 150 CUP
            2. 4GB, 14 días, $ 250
            3- 6 gigas por 35 dias 350 CUP
        """.trimIndent()

        val options = UssdPurchaseParser.parseMenu(menu)

        assertEquals(3, options.size)
        assertEquals(1, options[0].number)
        assertEquals(2.0, options[0].sizeGB!!, 0.001)
        assertEquals(7, options[0].days)
        assertEquals("150", options[0].price)
        assertEquals(4.0, options[1].sizeGB!!, 0.001)
        assertEquals(14, options[1].days)
        assertEquals(6.0, options[2].sizeGB!!, 0.001)
        assertEquals(35, options[2].days)
    }

    @Test
    fun `ignores lines that are not selectable packages`() {
        val menu = """
            Usted marco *133*1*4#
            Saldo actual: 1.5 GB
            1) 2 GB por 7 dias 150 CUP
        """.trimIndent()

        val options = UssdPurchaseParser.parseMenu(menu)

        assertEquals(1, options.size)
        assertEquals(1, options[0].number)
    }

    @Test
    fun `parses the real carrier menu with x prices and no days`() {
        val menu = """
            Seleccione la opcion deseada

            1 - 4.5GB x 240.00


            2 - 2GB +15min +20SMS x 120.00


            3 - 4GB +35min +40SMS x 240.00


            4 - 6GB +60min +70SMS x 360.00


            0 Menu
        """.trimIndent()

        val options = UssdPurchaseParser.parseMenu(menu)

        assertEquals(listOf(1, 2, 3, 4), options.map { it.number })
        assertEquals(4.5, options[0].sizeGB!!, 0.001)
        assertEquals("240.00", options[0].price)
        assertNull(options[0].days)
        assertEquals(2.0, options[1].sizeGB!!, 0.001)
        assertEquals("120.00", options[1].price)
        assertEquals(4.0, options[2].sizeGB!!, 0.001)
        assertEquals("240.00", options[2].price)
        assertEquals(6.0, options[3].sizeGB!!, 0.001)
        assertEquals("360.00", options[3].price)
    }

    @Test
    fun `detects the confirm with 1 prompt`() {
        val result = UssdPurchaseParser.parseConfirmation(
            "¿Desea comprar el plan de 4.5GB x 240.00? Marque 1 para confirmar"
        )

        assertFalse(result.success)
        assertTrue(result.needsConfirmation)
        assertEquals(4.5, result.sizeGB!!, 0.001)
    }

    @Test
    fun `detects a successful purchase`() {
        val result = UssdPurchaseParser.parseConfirmation(
            "Compra exitosa. Paquete de 4 GB valido por 35 dias activado."
        )

        assertTrue(result.success)
        assertFalse(result.needsConfirmation)
        assertEquals(4.0, result.sizeGB!!, 0.001)
        assertEquals(35, result.days)
    }

    @Test
    fun `parses the real ETECSA confirmation SMS`() {
        val sms = "ETECSA Plan Nacional: Se ha comprado Plan 4GB+35MIN+40SMS valido por 35 dias, " +
            "se activa con el primer uso. Se desconto 240.00 CUP. Saldo actual 43.30 CUP"

        val result = UssdPurchaseParser.parseSmsConfirmation(sms)

        assertTrue(result.success)
        assertFalse(result.failure)
        assertEquals(4.0, result.sizeGB!!, 0.001)
        assertEquals(35, result.days)
        assertEquals("240.00", result.price)
    }

    @Test
    fun `a balance SMS is not treated as a purchase confirmation`() {
        val result = UssdPurchaseParser.parseSmsConfirmation("Saldo actual: 4.5 GB. Saldo monetario 43.30 CUP")

        assertFalse(result.success)
        assertFalse(result.failure)
    }

    @Test
    fun `failure words win over success words`() {
        val result = UssdPurchaseParser.parseConfirmation(
            "No se pudo activar el paquete. Exito en la operacion anterior."
        )

        assertFalse(result.success)
    }

    @Test
    fun `an unrelated answer is not treated as success`() {
        val result = UssdPurchaseParser.parseConfirmation("Usted marco *133*1*4#")

        assertFalse(result.success)
        assertNull(result.sizeGB)
    }

    @Test
    fun `placeholder dialog is not mistaken for a menu or a confirmation`() {
        val placeholder = "Usted marco *133*1*4#"
        val result = UssdPurchaseParser.parseConfirmation(placeholder)

        assertTrue(UssdPurchaseParser.parseMenu(placeholder).isEmpty())
        assertFalse(result.success)
        assertFalse(result.needsConfirmation)
        assertFalse(result.failure)
    }

    @Test
    fun `a completed purchase SMS variant is accepted`() {
        val sms = "ETECSA: Compra realizada con exito. Plan 4.5GB activo por 35 dias, " +
            "se desconto 240.00 CUP. Saldo actual 12.00 CUP"

        val result = UssdPurchaseParser.parseSmsConfirmation(sms)

        assertTrue(result.success)
        assertFalse(result.failure)
        assertEquals(4.5, result.sizeGB!!, 0.001)
        assertEquals(35, result.days)
        assertEquals("240.00", result.price)
    }
}
