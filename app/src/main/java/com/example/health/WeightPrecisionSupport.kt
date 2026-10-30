package com.example.health

import java.math.BigDecimal
import java.math.RoundingMode

/** Renpho/Health Connect weights are stored with two decimal places by truncation, never rounding. */
object WeightPrecisionSupport {
    fun truncateKg(valueKg: Double): Float {
        require(valueKg.isFinite() && valueKg >= 0.0) { "Waga musi być nieujemną, skończoną wartością." }
        return BigDecimal.valueOf(valueKg)
            .setScale(2, RoundingMode.DOWN)
            .toFloat()
    }

    fun truncateKg(valueKg: Float): Float = truncateKg(valueKg.toDouble())
}
