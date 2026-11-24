package varify.event

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.*
import varify.config.*
import varify.engine.*
import varify.generator.*
import varify.syntax.*

class VarifyStatisticsListenerTest {
    private var now = 0L
    private val listener = VarifyStatisticsListener { now += 5_000_000; now } // Every stage takes 5 ms.
    private val configuration = VarifyConfiguration(randomizer = TestRandomizer(seed = 42)).withEventListener(listener)
    private val entries = mutableListOf<Map<String, String>>()

    private fun statistics(): List<String> =
        entries.mapNotNull { it[STATISTICS_REPORT_KEY] }

    @Test
    fun `reports attempts and samples per position with their most frequent values`() {
        varify(configuration, reporter = { entries += it }) {
            val letter = some(Values("letters", "a", "b"))
            val digit = some(Values("digits", 1, 2, 3))
            Assumptions.assumeFalse(letter == "b" && digit == 3)
        }

        expectThat(statistics()).containsExactly(
            """
            Varify statistics: seed 42
            variation: 6 attempts (1 rejected), all variations tried, 5 ms
            samples:
              [0] Values(letters): 6 samples, 2 distinct, most frequent: "a" (3), "b" (3)
              [1] Values(digits): 6 samples, 3 distinct, most frequent: 2 (2), 3 (2), 1 (2)
            """.trimIndent(),
        )
    }

    @Test
    fun `reports generators at the same position separately`() {
        varify(configuration, reporter = { entries += it }) {
            if (some(Values("flags", true, false)) == true) some(Shrinkable(1)) else some(Values("letters", "x"))
        }

        expectThat(statistics()).containsExactly(
            """
            Varify statistics: seed 42
            variation: 2 attempts, all variations tried, 5 ms
            samples:
              [0] Values(flags): 2 samples, 2 distinct
              [1] Shrinkable(1): 1 sample, 1 distinct
              [1] Values(letters): 1 sample, 1 distinct
            """.trimIndent(),
        )
    }

    @Test
    fun `reports shrink attempts but not their samples`() {
        expectThrows<AssertionError> {
            varify(configuration, reporter = { entries += it }) {
                val first = some(Shrinkable(1))
                val second = some(Shrinkable(3))
                Assumptions.assumeTrue(second > 1)
                if (first == 0) throw IllegalStateException("different")
                throw AssertionError("fails with $first $second")
            }
        }

        expectThat(statistics()).containsExactly(
            """
            Varify statistics: seed 42
            variation: 1 attempt, failed, 5 ms
            shrinking: 3 attempts (1 ignored, 1 diverged), exhausted, 5 ms
            samples:
              [0] Shrinkable(1): 1 sample, 1 distinct
              [1] Shrinkable(3): 1 sample, 1 distinct
            """.trimIndent(),
        )
    }

    @Test
    fun `reports an aborted varification when Varify itself fails`() {
        expectThrows<VarifyConfigurationException> {
            varify(configuration, reporter = { entries += it }) {
                some(Values("nothing"))
            }
        }

        expectThat(statistics()).containsExactly(
            """
            Varify statistics: seed 42
            variation: 1 attempt, aborted (VarifyConfigurationException: Generator nothing generated empty sample sequence), 5 ms
            """.trimIndent(),
        )
    }

    @Test
    fun `starts new statistics for each varification`() {
        repeat(2) {
            varify(configuration, reporter = { entries += it }) {
                some(Shrinkable(0))
            }
        }

        expectThat(statistics()).hasSize(2).all {
            isEqualTo(
                """
                Varify statistics: seed 42
                variation: 1 attempt, all variations tried, 5 ms
                samples:
                  [0] Shrinkable(0): 1 sample, 1 distinct
                """.trimIndent(),
            )
        }
    }

    private class Values(private val name: String, vararg val values: Any) : Generator<Any> {
        override val key = generatorKey<Values>(name)

        override fun generate(randomizer: Randomizer): Sequence<Sample<Any>> =
            values.asSequence().map(::Unshrinkable)

        override fun toString(): String = name
    }
}
