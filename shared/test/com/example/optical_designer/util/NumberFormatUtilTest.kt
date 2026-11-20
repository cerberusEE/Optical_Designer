package com.example.optical_designer.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatUtilTest {

    @Test
    fun testFormatToDisplayWithAtMostThreeSigFigs() {
        assertEquals("0", NumberFormatUtil.formatToDisplay(0.0))
        assertEquals("123", NumberFormatUtil.formatToDisplay(123.456))
        assertEquals("12.3", NumberFormatUtil.formatToDisplay(12.3456))
        assertEquals("1.23", NumberFormatUtil.formatToDisplay(1.23456))
        assertEquals("0.123", NumberFormatUtil.formatToDisplay(0.123456))
        assertEquals("0.0123", NumberFormatUtil.formatToDisplay(0.0123456))
        assertEquals("0.00123", NumberFormatUtil.formatToDisplay(0.00123456))
        assertEquals("1.23e-4", NumberFormatUtil.formatToDisplay(0.000123456))
        assertEquals("1.23e6", NumberFormatUtil.formatToDisplay(1234567.8))
        assertEquals("100", NumberFormatUtil.formatToDisplay(100.0))
        assertEquals("100", NumberFormatUtil.formatToDisplay(99.99))
        assertEquals("0.24", NumberFormatUtil.formatToDisplay(0.24000000000000005))
    }

    @Test
    fun testUnitFormattersWithSigFigs() {
        assertEquals("12.3 mW", NumberFormatUtil.formatPower(0.0123456))
        assertEquals("1.23 W", NumberFormatUtil.formatPower(1.23456))
        assertEquals("180 mm", NumberFormatUtil.formatDistanceUnit(180.0))
        assertEquals("1.8 m", NumberFormatUtil.formatDistanceUnit(1800.0))
        assertEquals("1.23 W/m² (0.123 mW/cm²)", NumberFormatUtil.formatIrradiance(1.23456))
    }
}
