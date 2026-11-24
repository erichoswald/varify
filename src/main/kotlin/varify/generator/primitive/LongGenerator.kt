package varify.generator.primitive

import varify.config.*
import varify.generator.*

data class LongGenerator(
    val range: LongRange = Long.MIN_VALUE..Long.MAX_VALUE,
    val specialValues: List<Long> = listOf(0, 1, -1, range.first, range.last),
    val shrinker: LongShrinker = BisectingLongShrinker.towardsZero(range),
) : Generator<Long> {
    init {
        checkConfiguration(!range.isEmpty()) { "range $range must not be empty" }
    }

    override val key = generatorKey<LongGenerator>(range, specialValues, shrinker)

    // The special values first, then the other values in random order, each once (so small ranges exhaust themselves).
    override fun generate(randomizer: Randomizer): Sequence<Sample<Long>> {
        val specials = specialValues.filter { it in range }.toSet()
        val others = randomizer.permutation(range).filter { it !in specials }
        return (specials.asSequence() + others).map { LongSample(it, shrinker) }
    }
}

@Suppress("ClassName")
object long {
    /** Generates longs from [min] to [max] (both inclusive). */
    operator fun invoke(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): LongGenerator =
        LongGenerator(min..max)

    /** Generates longs in [range]. */
    operator fun invoke(range: LongRange): LongGenerator =
        LongGenerator(range)

    /** Generates longs from 0 to [max] (inclusive). */
    fun nonNegative(max: Long = Long.MAX_VALUE): LongGenerator =
        LongGenerator(0..max)

    /** Generates longs from 1 to [max] (inclusive). */
    fun positive(max: Long = Long.MAX_VALUE): LongGenerator =
        LongGenerator(1..max)
}
