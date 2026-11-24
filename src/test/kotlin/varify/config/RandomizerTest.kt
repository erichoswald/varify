package varify.config

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*

class RandomizerTest {

    @Test
    fun `The permutation yields each value of the range exactly once`() {
        val randomizer = Randomizer.System()

        for (size in (0L..130L) + listOf(1000L, 1024L, 1025L)) {
            val range = -50L..<size - 50
            expectThat(randomizer.permutation(range).toList().sorted()).isEqualTo(range.toList())
        }
    }

    @Test
    fun `The permutation shuffles the positions`() {
        val positions = Randomizer.System().permutation(0L..<1000L).toList()

        expectThat(positions).isNotEqualTo((0L..<1000L).toList())
        expectThat(positions.zipWithNext().count { (a, b) -> b == a + 1 }).isLessThan(10)
    }

    @Test
    fun `The permutation of a huge range yields its first values without computing the others`() {
        val values = Randomizer.System().permutation(0L..Long.MAX_VALUE).take(1000).toList()

        expectThat(values).all { isGreaterThanOrEqualTo(0L) }
        expectThat(values.toSet()).hasSize(1000)
    }

    @Test
    fun `The permutation of the full Long range yields values from all over the range`() {
        val values = Randomizer.System().permutation(Long.MIN_VALUE..Long.MAX_VALUE).take(1000).toList()

        expectThat(values.toSet()).hasSize(1000)
        expectThat(values.count { it < 0 }).isGreaterThan(300).isLessThan(700)
    }

    @Test
    fun `The permutation depends only on the seed`() {
        val first = Randomizer.System(seed = 42).permutation(0L..<100_000L).take(100).toList()
        val second = Randomizer.System(seed = 42).permutation(0L..<100_000L).take(100).toList()
        val other = Randomizer.System(seed = 43).permutation(0L..<100_000L).take(100).toList()

        expectThat(first).isEqualTo(second).isNotEqualTo(other)
    }
}
