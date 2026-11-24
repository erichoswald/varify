package varify.event

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.*
import varify.config.*
import varify.engine.*
import varify.generator.*
import varify.syntax.*

class VarifyDebugListenerTest {
    private val listener = VarifyDebugListener()
    private val configuration = VarifyConfiguration(randomizer = TestRandomizer(seed = 42)).withEventListener(listener)
    private val entries = mutableListOf<Map<String, String>>()

    private fun debugLogs(): List<String> =
        entries.mapNotNull { it[DEBUG_REPORT_KEY] }

    @Test
    fun `logs passed and rejected attempts with their samples`() {
        var calls = 0

        varify(configuration, reporter = { entries += it }) {
            some(Shrinkable(5))
            some(Unshrinkables("", "a b\n"))
            Assumptions.assumeTrue(++calls != 2, "second call")
        }

        expectThat(debugLogs()).containsExactly(
            """
            Varify debug: seed 42
            variation 1: passed
              [0] Shrinkable(5) = 5
              [1] Unshrinkables(2) = "a b\n"
            variation 2: rejected (TestAbortedException: Assumption failed: second call)
              [0] Shrinkable(5) = 5
              [1] Unshrinkables(2) = ""
            all variations tried after 2 attempts
            """.trimIndent(),
        )
    }

    @Test
    fun `logs shrink attempts with their outcomes and marks the shrunk sample`() {
        expectThrows<AssertionError> {
            varify(configuration, reporter = { entries += it }) {
                val first = some(Shrinkable(1))
                val second = some(Shrinkable(3))
                Assumptions.assumeTrue(second > 1)
                if (first == 0) throw IllegalStateException("different\nsecond line")
                throw AssertionError("fails with $first $second")
            }
        }

        expectThat(debugLogs()).containsExactly(
            """
            Varify debug: seed 42
            variation 1: failed (AssertionError: fails with 1 3)
              [0] Shrinkable(1) = 1
              [1] Shrinkable(3) = 3
            shrink 1: diverged (IllegalStateException: different)
             *[0] Shrinkable(1) = 0
              [1] Shrinkable(3) = 3
            shrink 2: failed the same way (AssertionError: fails with 1 2)
              [0] Shrinkable(1) = 1
             *[1] Shrinkable(3) = 2
            shrink 3: ignored
              [0] Shrinkable(1) = 1
             *[1] Shrinkable(3) = 1
            shrinking exhausted after 3 attempts
            """.trimIndent(),
        )
    }

    @Test
    fun `logs the attempt in progress when Varify itself fails`() {
        expectThrows<VarifyConfigurationException> {
            varify(configuration, reporter = { entries += it }) {
                some(Unshrinkables())
            }
        }

        expectThat(debugLogs()).containsExactly(
            """
            Varify debug: seed 42
            variation 1: aborted (VarifyConfigurationException: Generator Unshrinkables generated empty sample sequence)
            """.trimIndent(),
        )
    }

    @Test
    fun `starts a new log for each varification`() {
        repeat(2) {
            varify(configuration.copy(randomizer = TestRandomizer(seed = it.toLong())), reporter = { entries += it }) {
                some(Shrinkable(0))
            }
        }

        expectThat(debugLogs()).containsExactly(
            """
            Varify debug: seed 0
            variation 1: passed
              [0] Shrinkable(0) = 0
            all variations tried after 1 attempt
            """.trimIndent(),
            """
            Varify debug: seed 1
            variation 1: passed
              [0] Shrinkable(0) = 0
            all variations tried after 1 attempt
            """.trimIndent(),
        )
    }

    private class Unshrinkables(vararg val values: String) : Generator<String> {
        override val key = generatorKey<Unshrinkables>(values.size)

        override fun generate(randomizer: Randomizer): Sequence<Sample<String>> =
            values.asSequence().map(::Unshrinkable)

        override fun toString(): String = "Unshrinkables"
    }
}
