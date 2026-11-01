package fit.eveship.esf.internal

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// Floats as decimals (SPEC §6.5, §8).

/** The shortest decimal, without exponent, that reads back as the same float. */
internal fun formatNumber(value: Double): String {
    if (value == 0.0) {
        return "0"
    }
    val digits = shortest(Math.abs(value)).toPlainString()
    return if (value < 0) "-$digits" else digits
}

/** The fewest significant digits that read back as a positive, finite float; the closest where several do. */
internal fun shortest(value: Double): BigDecimal {
    val exact = BigDecimal(value)
    for (precision in 1..16) {
        val down = exact.round(MathContext(precision, RoundingMode.FLOOR))
        val up = exact.round(MathContext(precision, RoundingMode.CEILING))
        val candidates = listOf(down, up).filter { it.toDouble() == value }
        if (candidates.isNotEmpty()) {
            return candidates.minBy { (it - exact).abs() }.stripTrailingZeros()
        }
    }
    return exact.round(MathContext(17, RoundingMode.HALF_EVEN)).stripTrailingZeros()
}

/** floor(a / b) of the shortest decimals of two positive floats, at most Long.MAX_VALUE. */
internal fun quotient(
    a: Double,
    b: Double,
): Long {
    val q = shortest(a).divide(shortest(b), 0, RoundingMode.DOWN)
    return if (q > BigDecimal.valueOf(Long.MAX_VALUE)) Long.MAX_VALUE else q.toLong()
}
