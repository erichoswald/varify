package varify.event

import org.junit.jupiter.api.*
import org.opentest4j.*
import strikt.api.*
import strikt.assertions.*
import varify.*
import varify.config.*
import varify.engine.*
import varify.syntax.*

class CollectedStatisticsTest {
    private val configuration = VarifyConfiguration(randomizer = TestRandomizer(seed = 42))
    private val entries = mutableListOf<Map<String, String>>()

    private fun summary(): String? =
        entries.single()[SUMMARY_REPORT_KEY]

    private fun Assertion.Builder<out Throwable>.summary(): Assertion.Builder<String?> =
        get { suppressed.single() }.isA<VarifyReport>().get { message }

    @Test
    fun `reports collected values sorted by frequency`() {
        varify(configuration, reporter = { entries += it }) {
            val value = oneOf(1..8)
            collect("parity", if (value % 2 == 0) "even" else "odd")
            collect("small", value <= 2)
        }

        expectThat(summary()).isEqualTo(
            """
            Varify: passed 8 attempts, all variations tried
              seed: 42
              collected parity: "odd" (50%), "even" (50%)
              collected small: false (75%), true (25%)
            """.trimIndent(),
        )
    }

    @Test
    fun `reports at most ten values per label`() {
        varify(configuration, reporter = { entries += it }) {
            collect("value", oneOf(1..12))
        }

        expectThat(summary()).isNotNull().endsWith(", … 2 more")
    }

    @Test
    fun `counts every call but neither rejected nor shrinking attempts`() {
        var attempts = 0

        expectThrows<AssertionError> {
            varify(configuration) {
                some(Shrinkable(3))
                oneOf(1, 2)
                collect("attempt", ++attempts)
                collect("calls", "first")
                collect("calls", "second")
                Assumptions.assumeTrue(attempts > 1) // Rejects the first attempt only.
                throw AssertionError("fails")
            }
        }.summary().isNotNull().and {
            contains("failed on attempt 2 (1 rejected)")
            contains("\n  collected attempt: 2 (100%)\n  collected calls: \"first\" (50%), \"second\" (50%)")
        }
    }

    @Test
    fun `passes when coverage requirements are met and reports them`() {
        varify(configuration, reporter = { entries += it }) {
            cover("small", oneOf(1..4) == 1, minPercent = 25)
        }

        expectThat(summary()).isNotNull().endsWith("\n  covered small: 25% (required 25%)")
    }

    @Test
    fun `fails when a coverage requirement is not met`() {
        expectThrows<AssertionFailedError> {
            varify(configuration) {
                val value = oneOf(1..4)
                cover("small", value == 1, minPercent = 30)
                cover("large", value >= 3, minPercent = 50)
            }
        }.and {
            get { message }.isEqualTo("Coverage of small is 25% (1 of 4 calls), required 30%")
            summary().isNotNull().endsWith("\n  covered small: 25% (required 30%)\n  covered large: 50% (required 50%)")
        }
    }

    @Test
    fun `does not check coverage when an attempt fails`() {
        expectThrows<AssertionError> {
            varify(configuration) {
                cover("never", false, minPercent = 100)
                throw AssertionError("fails")
            }
        }.get { message }.isEqualTo("fails")
    }

    @Test
    fun `rejects different requirements for the same label`() {
        expectThrows<VarifyConfigurationException> {
            varify(configuration) {
                cover("small", oneOf(1, 2) == 1, minPercent = 10)
                cover("small", false, minPercent = 20)
            }
        }.get { message }.isEqualTo("cover(\"small\") requires both 10% and 20%")
    }

    @Test
    fun `rejects requirements outside of 0 to 100 percent`() {
        expectThrows<VarifyConfigurationException> {
            varify(configuration) {
                cover("small", true, minPercent = 101)
            }
        }.get { message }.isEqualTo("cover(\"small\", minPercent = 101) must be in 0..100")
    }
}
