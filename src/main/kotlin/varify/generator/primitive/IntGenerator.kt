package varify.generator.primitive

import varify.config.*
import varify.generator.*

data class IntGenerator(
    val range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    val specialValues: List<Int> = listOf(0, 1, -1, range.first, range.last),
    val shrinker: IntShrinker = BisectingIntShrinker.towardsZero(range),
) : Generator<Int> {
    init {
        checkConfiguration(!range.isEmpty()) { "range $range must not be empty" }
    }

    override val key = generatorKey<IntGenerator>(range, specialValues, shrinker)

    // The special values first, then the other values in random order, each once (so small ranges exhaust themselves).
    override fun generate(randomizer: Randomizer): Sequence<Sample<Int>> {
        val specials = specialValues.filter { it in range }.toSet()
        val others = randomizer.permutation(range.first.toLong()..range.last.toLong())
            .map(Long::toInt)
            .filter { it !in specials }
        return (specials.asSequence() + others).map { IntSample(it, shrinker) }
    }
}

@Suppress("ClassName")
object int {
    /** Generates integers from [min] to [max] (both inclusive). */
    operator fun invoke(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): IntGenerator =
        IntGenerator(min..max)

    /** Generates integers in [range]. */
    operator fun invoke(range: IntRange): IntGenerator =
        IntGenerator(range)

    /** Generates integers from 0 to [max] (inclusive), e.g. collection sizes. */
    fun nonNegative(max: Int = Int.MAX_VALUE): IntGenerator =
        IntGenerator(0..max)

    /** Generates integers from 1 to [max] (inclusive). */
    fun positive(max: Int = Int.MAX_VALUE): IntGenerator =
        IntGenerator(1..max)
}
