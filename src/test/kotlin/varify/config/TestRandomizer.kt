package varify.config

import varify.extensions.*

class TestRandomizer(override val seed: Long = 0) : Randomizer {

    private var next = seed.toInt()

    override fun selectFrom(range: IntRange): Int =
        (range.first + Math.floorMod(next++.toLong(), range.size())).toInt()

    override fun <T> selectFrom(elements: List<T>): T =
        elements[Math.floorMod(next++, elements.size)]

    override fun <T> shuffle(values: Iterable<T>): List<T> =
        values.reversed() // Deterministic permutation that makes shuffling observable in tests.

    override fun permutation(range: LongRange): Sequence<Long> =
        range.reversed().asSequence() // Reversed like shuffle.
}
