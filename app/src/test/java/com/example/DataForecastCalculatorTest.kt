package com.example

import com.example.domain.DataForecastCalculator
import com.example.domain.DataPaceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataForecastCalculatorTest {

    @Test
    fun testDataForecast_willReachMonthEnd() {
        // 10 GB (10240 MB) package, 30 days cycle
        // Day 10 of 30 (20 days remaining)
        // Remaining balance: 8000 MB (consumed: 2240 MB in 10 days)
        // Burn rate: 224 MB/day
        // Days of data left: 8000 / 224 = 35.7 days -> Greater than 20 days remaining!
        val result = DataForecastCalculator.calculate(
            packageTotalMB = 10240.0,
            remainingBalanceMB = 8000.0,
            totalCycleDays = 30,
            daysElapsed = 10
        )

        assertTrue(result.willLastWholeMonth)
        assertEquals(224.0, result.dailyBurnRateMB, 0.001)
        assertEquals(400.0, result.safeDailyAllowanceMB, 0.001)
        assertEquals(DataPaceStatus.SURPLUS_SAFE, result.paceStatus)
        // Projected surplus: 8000 - (224 * 20) = 8000 - 4480 = 3520 MB
        assertEquals(3520.0, result.projectedDeficitOrSurplusMB, 0.001)
    }

    @Test
    fun testDataForecast_willRunOutEarly() {
        // 10 GB (10240 MB) package, 30 days cycle
        // Day 12 of 30 (18 days remaining)
        // Remaining balance: 3480 MB (consumed: 6760 MB in 12 days)
        // Burn rate: 6760 / 12 = 563.33 MB/day
        // Days of data left: 3480 / 563.33 = 6.17 days (will run out in ~6 days!)
        val result = DataForecastCalculator.calculate(
            packageTotalMB = 10240.0,
            remainingBalanceMB = 3480.0,
            totalCycleDays = 30,
            daysElapsed = 12
        )

        assertFalse(result.willLastWholeMonth)
        assertEquals(6.17, result.daysOfDataLeft, 0.05)
        // Exhaustion cycle day: 12 + 6 = 18
        assertEquals(18, result.exhaustionCycleDay)
        // Safe allowance for 18 remaining days: 3480 / 18 = 193.33 MB/day
        assertEquals(193.33, result.safeDailyAllowanceMB, 0.05)
        assertEquals(DataPaceStatus.CRITICAL_DEPLETED, result.paceStatus)
    }

    @Test
    fun testDataForecast_whatIfSimulation() {
        val result = DataForecastCalculator.calculate(
            packageTotalMB = 10240.0,
            remainingBalanceMB = 3480.0,
            totalCycleDays = 30,
            daysElapsed = 12,
            simulatedDailyMB = 150.0 // Reduced from 563 to 150 MB/day
        )

        assertNotNull(result.simulatedDaysLeft)
        // 3480 / 150 = 23.2 days of data left!
        assertEquals(23.2, result.simulatedDaysLeft!!, 0.01)
        // Will last through remaining 18 days: 3480 - (150 * 18) = 780 MB left
        assertEquals(780.0, result.simulatedEndBalanceMB!!, 0.01)
    }

    @Test
    fun testDataForecast_activitiesCalculations() {
        val result = DataForecastCalculator.calculate(
            packageTotalMB = 10240.0,
            remainingBalanceMB = 3000.0,
            totalCycleDays = 30,
            daysElapsed = 15
        )

        // HD video uses 1500 MB/hr -> 3000 / 1500 = 2.0 hours
        val videoActivity = result.activities.first { it.iconType == "video" }
        assertEquals(2.0, videoActivity.hoursAvailableTotal, 0.001)
    }

    @Test
    fun testDataForecast_customPackageChange() {
        // Change package from 10 GB to 25 GB (25600 MB) with 18000 MB remaining on Day 10 of 30
        val result = DataForecastCalculator.calculate(
            packageTotalMB = 25600.0,
            remainingBalanceMB = 18000.0,
            totalCycleDays = 30,
            daysElapsed = 10
        )

        assertEquals(25600.0, result.packageTotalMB, 0.001)
        assertEquals(18000.0, result.remainingBalanceMB, 0.001)
        assertEquals(7600.0, result.consumedMB, 0.001)
        // Consumed 7600 in 10 days -> 760 MB/day
        assertEquals(760.0, result.dailyBurnRateMB, 0.001)
        // Days of data left: 18000 / 760 = 23.68 days (days remaining is 20) -> will last!
        assertTrue(result.willLastWholeMonth)
        assertEquals(900.0, result.safeDailyAllowanceMB, 0.001) // 18000 / 20 = 900 MB/day
    }

    @Test
    fun testDataForecast_prepaid35DaysCycle() {
        // Line 2: 6 GB (6144 MB) prepaid package with 35-day validity
        // Day 7 of 35 (28 days remaining)
        // Remaining: 4500 MB -> Consumed: 1644 MB in 7 days
        val result = DataForecastCalculator.calculate(
            packageTotalMB = 6144.0,
            remainingBalanceMB = 4500.0,
            totalCycleDays = 35,
            daysElapsed = 7
        )

        assertEquals(35, result.totalCycleDays)
        assertEquals(7, result.daysElapsed)
        // Day 7 of 35 -> 29 days remaining counting today (operator semantics)
        assertEquals(29, result.daysRemaining)
        assertEquals(1644.0, result.consumedMB, 0.001)
        // Burn rate: 1644 / 7 = 234.857 MB/day
        assertEquals(234.86, result.dailyBurnRateMB, 0.01)
        // Safe allowance: 4500 / 28 = 160.71 MB/day
        assertEquals(160.71, result.safeDailyAllowanceMB, 0.01)
        // Days left: 4500 / 234.857 = 19.16 days < 28 days -> will run out early!
        assertFalse(result.willLastWholeMonth)
        // Exhaustion cycle day: 7 + 19 = 26
        assertEquals(26, result.exhaustionCycleDay)
    }

    @Test
    fun testDataForecast_operatorDaysMatchExactly() {
        // The carrier answers "validos 14 dias" on a 35-day package.
        // applyUssdCapture anchors the cycle to day 35 - 14 + 1 = 22; the
        // forecast must then show EXACTLY 14 days remaining, like the operator.
        val result = DataForecastCalculator.calculate(
            packageTotalMB = 6144.0,
            remainingBalanceMB = 2048.0,
            totalCycleDays = 35,
            daysElapsed = 35 - 14 + 1
        )

        assertEquals(22, result.daysElapsed)
        assertEquals(14, result.daysRemaining)
    }

    @Test
    fun testDataForecast_lastDayOfCycleCountsToday() {
        // On the last day of the cycle today still counts: 1 day remaining.
        val result = DataForecastCalculator.calculate(
            packageTotalMB = 6144.0,
            remainingBalanceMB = 100.0,
            totalCycleDays = 35,
            daysElapsed = 35
        )

        assertEquals(35, result.daysElapsed)
        assertEquals(1, result.daysRemaining)
    }

    @Test
    fun testDataForecast_expiredPackageSurfacesVencido() {
        // Purchase 2 days past the end of a 35-day cycle: the display day
        // stays clamped at 35 but daysRemaining must go to zero/negative so
        // the "Vencido" warning can trigger.
        val result = DataForecastCalculator.calculate(
            packageTotalMB = 6144.0,
            remainingBalanceMB = 100.0,
            totalCycleDays = 35,
            daysElapsed = 37
        )

        assertEquals(35, result.daysElapsed)
        assertTrue(result.daysRemaining <= 0)
    }

    @Test
    fun testUssdParser_extractsFromCarrierMessages() {
        // ETECSA style message with GB
        val msg1 = "ETECSA: Datos activos. Le quedan 4.25 GB validos hasta el 15/12/2026."
        val p1 = com.example.domain.UssdParser.parse(msg1)
        assertNotNull(p1)
        assertEquals(4.25 * 1024.0, p1!!.valueMB, 0.001)
        assertEquals("GB", p1.detectedUnit)

        // Message with MB and comma decimal
        val msg2 = "Saldo de datos restantes: 3500 MB."
        val p2 = com.example.domain.UssdParser.parse(msg2)
        assertNotNull(p2)
        assertEquals(3500.0, p2!!.valueMB, 0.001)
        assertEquals("MB", p2.detectedUnit)

        // Direct number input with default unit
        val p3 = com.example.domain.UssdParser.parse("2.5", defaultUnit = "GB")
        assertNotNull(p3)
        assertEquals(2.5 * 1024.0, p3!!.valueMB, 0.001)

        val p4 = com.example.domain.UssdParser.parse("800", defaultUnit = "MB")
        assertNotNull(p4)
        assertEquals(800.0, p4!!.valueMB, 0.001)
    }
}
