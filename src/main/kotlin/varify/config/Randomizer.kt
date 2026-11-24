package varify.config

import kotlin.random.Random
import kotlin.random.nextInt

interface Randomizer {
    val seed: Long

    fun selectFrom(range: IntRange): Int
    fun <T> selectFrom(elements: List<T>): T

    fun <T> shuffle(values: Iterable<T>): List<T>

    /** Lazily computes the values of [range] in random order, each once. */
    fun permutation(range: LongRange): Sequence<Long>

    class System(override val seed: Long = Random.nextLong()) : Randomizer {
        private val random = Random(seed)

        override fun selectFrom(range: IntRange): Int =
            random.nextInt(range)

        override fun <T> selectFrom(elements: List<T>): T =
            elements[random.nextInt(elements.size)]

        override fun <T> shuffle(values: Iterable<T>): List<T> =
            values.shuffled(random)

        // The wrapping difference is the last position as an unsigned number, even for the full Long range.
        override fun permutation(range: LongRange): Sequence<Long> =
            if (range.isEmpty()) {
                emptySequence()
            } else {
                FeistelPermutation(range.last - range.first, random::nextLong).asSequence().map { range.first + it }
            }
    }
}
