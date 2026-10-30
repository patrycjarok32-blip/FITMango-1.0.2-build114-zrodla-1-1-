package com.example.health

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightPrecisionSupportTest {
    @Test fun truncatesToTwoDecimalPlacesWithoutRounding() {
        assertEquals(72.38f, WeightPrecisionSupport.truncateKg(72.389), 0.0001f)
        assertEquals(72.30f, WeightPrecisionSupport.truncateKg(72.305), 0.0001f)
        assertEquals(72.30f, WeightPrecisionSupport.truncateKg(72.3), 0.0001f)
        assertEquals(0.0f, WeightPrecisionSupport.truncateKg(0.009), 0.0001f)
    }
}
