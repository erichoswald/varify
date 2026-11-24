package varify

import org.junit.jupiter.api.*
import org.opentest4j.TestAbortedException
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.engine.*
import varify.event.*
import varify.generator.primitive.*
import varify.syntax.*

class VarifyTest {
    private val configuration = VarifyConfiguration(randomizer = TestRandomizer(seed = 42))

    @Test
    fun `varify, when the test function passes, completes normally`() {
        expectCatching {
            varify(configuration) {
                some(Shrinkable(5))
            }
        }.isSuccess()
    }

    @Test
    fun `varify publishes events to configured listeners, starting with the original configuration`() {
        val recorder = VarifyEventRecorder()

        varify(configuration.withEventListener(recorder)) {
            some(Shrinkable(5))
        }

        expectThat(recorder.events).first().isEqualTo(VarificationStarted(configuration.withEventListener(recorder), VarifyReporter.None))
        expectThat(recorder.events.takeLast(2)).containsExactly(VariationsExhausted(attempts = 1), VarificationFinished(null))
    }

    @Test
    fun `varify, when run again with the same seed, generates the same samples`() {
        fun samples(seed: Long): List<Pair<Int, Long>> =
            mutableListOf<Pair<Int, Long>>().also { samples ->
                varify(VarifyConfiguration(randomizer = Randomizer.System(seed))) {
                    samples += some(int()) to some(long())
                }
            }

        expectThat(samples(42)).isEqualTo(samples(42))
        expectThat(samples(42)).isNotEqualTo(samples(43))
    }

    @Test
    fun `varify passes the reporter to event listeners`() {
        val entries = mutableListOf<Map<String, String>>()
        val listener = VarifyEventListener { event ->
            if (event is VarificationStarted) {
                event.reporter.report("seed", event.configuration.randomizer.seed)
            }
        }

        varify(configuration.withEventListener(listener), reporter = { entries += it }) {
            some(Shrinkable(5))
        }

        expectThat(entries).first().isEqualTo(mapOf("seed" to "42"))
    }

    @Test
    fun `varify, when all variations pass, reports a summary`() {
        val entries = mutableListOf<Map<String, String>>()

        varify(configuration, reporter = { entries += it }) {
            some(Shrinkable(5))
        }

        expectThat(entries).containsExactly(
            mapOf(SUMMARY_REPORT_KEY to "Varify: passed 1 attempt, all variations tried\n  seed: 42"),
        )
    }

    @Test
    fun `varify, when it reaches the attempt limit without failure, reports a summary`() {
        val entries = mutableListOf<Map<String, String>>()

        varify(configuration.copy(variationAttemptsLimit = 2), reporter = { entries += it }) {
            oneOf(1, 2, 3)
        }

        expectThat(entries).containsExactly(
            mapOf(SUMMARY_REPORT_KEY to "Varify: passed 2 attempts, attempt limit reached\n  seed: 42"),
        )
    }

    @Test
    fun `varify, when attempts are rejected by assumptions, reports them in the summary`() {
        val entries = mutableListOf<Map<String, String>>()

        varify(configuration, reporter = { entries += it }) {
            Assumptions.assumeTrue(oneOf(1, 2, 3, 4) % 2 == 0)
        }

        expectThat(entries).containsExactly(
            mapOf(SUMMARY_REPORT_KEY to "Varify: passed 2 attempts (2 rejected), all variations tried\n  seed: 42"),
        )
    }

    @Test
    fun `varify, when every attempt is rejected by an assumption, aborts with a report`() {
        expectThrows<TestAbortedException> {
            varify(configuration) {
                oneOf(1, 2)
                Assumptions.assumeTrue(false)
            }
        }
            .get { suppressed.toList() }
            .single()
            .isA<VarifyReport>()
            .get { message }
            .isEqualTo("Varify: rejected all 2 attempts, all variations tried\n  seed: 42")
    }

    @Test
    fun `varify, when a test fails after rejected attempts, reports them in the summary`() {
        var calls = 0

        expectThrows<AssertionError> {
            varify(configuration) {
                val value = oneOf(1, 2, 3)
                Assumptions.assumeTrue(++calls > 1) // Rejects the first attempt only.
                throw AssertionError("fails with $value")
            }
        }
            .get { suppressed.toList() }
            .single()
            .isA<VarifyReport>()
            .get { message }
            .isNotNull()
            .startsWith("Varify: failed on attempt 2 (1 rejected), ")
    }

    @Test
    fun `varify, when the test function fails, reports no summary because the error carries it`() {
        val entries = mutableListOf<Map<String, String>>()

        expectThrows<AssertionError> {
            varify(configuration, reporter = { entries += it }) {
                throw AssertionError("fails with ${some(Shrinkable(0))}")
            }
        }

        expectThat(entries).isEmpty()
    }

    @Test
    fun `varify, when the test function fails, throws the shrunk error with a report`() {
        expectThrows<AssertionError> {
            varify(configuration) {
                val value = some(Shrinkable(5))
                if (value >= 2) throw AssertionError("too big: $value")
            }
        }
            .and { get { message }.isEqualTo("too big: 2") }
            .get { suppressed.toList() }
            .single()
            .isA<VarifyReport>()
            .and { get { cause }.isA<AssertionError>().get { message }.isEqualTo("too big: 5") }
            .get { message }
            .isEqualTo(
                """
                Varify: failed on attempt 1, shrunk in attempt 3 of 4
                  seed: 42
                  generated: 5
                  shrunk: 2
                """.trimIndent(),
            )
    }

    @Test
    fun `varify, when shrinking does not reproduce the failure, reports the original error without cause`() {
        var calls = 0

        expectThrows<AssertionError> {
            varify(configuration) {
                some(Shrinkable(5))
                if (++calls == 1) throw AssertionError("fails only once")
            }
        }
            .and { get { message }.isEqualTo("fails only once") }
            .get { suppressed.toList() }
            .single()
            .isA<VarifyReport>()
            .get { cause }
            .isNull()
    }

    @Test
    fun `varify, when the failing samples cannot be shrunk, reports them as unshrinkable`() {
        expectThrows<AssertionError> {
            varify(configuration) {
                val value = some(Shrinkable(0))
                throw AssertionError("fails with $value")
            }
        }
            .and { get { message }.isEqualTo("fails with 0") }
            .get { suppressed.toList() }
            .single()
            .isA<VarifyReport>()
            .and { get { cause }.isNull() }
            .get { message }
            .isEqualTo(
                """
                Varify: failed on attempt 1, unshrinkable
                  seed: 42
                  generated: 0
                """.trimIndent(),
            )
    }

    @Test
    fun `varify does not shrink to a sample that fails a different assertion of the same class`() {
        expectThrows<AssertionError> {
            varify(configuration) {
                val value = some(IntGenerator(896..896, shrinker = BisectingIntShrinker(0)))
                if (value < 10) throw AssertionError("too small: $value")
                if (value >= 300) throw AssertionError("too big: $value")
            }
        }.get { message }.isEqualTo("too big: 300")
    }

    @Test
    fun `varify, when the test function throws a non-assertion error, rethrows it with the seed reported`() {
        val error = IllegalStateException("not an assertion")

        expectThrows<IllegalStateException> {
            varify(configuration) {
                some(Shrinkable(5))
                throw error
            }
        }
            .isSameInstanceAs(error)
            .get { suppressed.toList() }
            .single()
            .isA<VarifyReport>()
            .get { message }
            .isNotNull()
            .contains("seed: 42")
    }
}
