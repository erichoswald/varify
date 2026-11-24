package varify.generator.primitive

fun interface IntShrinker {
    fun shrink(value: Int): List<Int>
}

/**
 * Shrinks towards a target value by repeatedly halving the remaining distance.
 *
 * The candidates are ordered from the most to the least aggressive. Because the shrinking stage continues with the first
 * failing candidate, it finds the smallest failing value of a monotonic test in O(log² n) attempts.
 */
data class BisectingIntShrinker(val targetValue: Int) : IntShrinker {
    override fun shrink(value: Int): List<Int> {
        val candidates = mutableListOf<Int>()
        var distance = value.toLong() - targetValue
        while (distance != 0L) {
            candidates += (value - distance).toInt()
            distance /= 2
        }
        return candidates
    }

    companion object {
        fun towardsZero(range: IntRange): BisectingIntShrinker {
            val target = when {
                range.first > 0 -> range.first
                range.last < 0 -> range.last
                else -> 0
            }
            return BisectingIntShrinker(target)
        }
    }
}
