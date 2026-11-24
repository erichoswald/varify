package varify.generator.primitive

fun interface LongShrinker {
    fun shrink(value: Long): List<Long>
}

/**
 * Shrinks towards a target value by repeatedly halving the remaining distance, like [BisectingIntShrinker].
 *
 * The distance between two longs may exceed `Long.MAX_VALUE`, so it is computed as an unsigned number. The candidates lie
 * between the value and the target, so the wrapping arithmetic of `Long` computes them correctly.
 */
data class BisectingLongShrinker(val targetValue: Long) : LongShrinker {
    override fun shrink(value: Long): List<Long> {
        val candidates = mutableListOf<Long>()
        val above = value > targetValue
        var distance = if (above) (value - targetValue).toULong() else (targetValue - value).toULong()
        while (distance != 0uL) {
            candidates += if (above) value - distance.toLong() else value + distance.toLong()
            distance /= 2u
        }
        return candidates
    }

    companion object {
        fun towardsZero(range: LongRange): BisectingLongShrinker {
            val target = when {
                range.first > 0 -> range.first
                range.last < 0 -> range.last
                else -> 0
            }
            return BisectingLongShrinker(target)
        }
    }
}
