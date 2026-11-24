package varify.engine

import org.junit.jupiter.api.*
import org.opentest4j.TestAbortedException
import strikt.api.*
import strikt.assertions.*
import varify.VarifyInternalException
import varify.config.*
import varify.event.*
import varify.generator.*
import varify.generator.primitive.*
import varify.syntax.*
import java.lang.reflect.InvocationTargetException

class VariationStageTest {
    private val recorder = VarifyEventRecorder()
    private val configuration = recorder.configuration()

    @Test
    fun `The variation stage, when a test function requests no samples, is immediately exhausted`() {
        VariationStage(configuration) { }

        expectThat(recorder.events).containsExactly(
            VariationInitiated(attempt = 1),
            VariationPassed(attempt = 1, generated = emptyList()),
            VariationsExhausted(attempts = 1),
        )
    }

    @Test
    fun `The variation stage, when it runs out of sample values, becomes exhausted`() {
        val variations = mutableListOf<Pair<Int, String>>()

        VariationStage(configuration) {
            variations += oneOf(1, 2, 3) to oneOf("a", "b")
        }

        expectThat(variations).containsExactlyInAnyOrder(
            1 to "a",
            1 to "b",
            2 to "a",
            2 to "b",
            3 to "a",
            3 to "b",
        )
        expectThat(recorder.events)
            .filterIsInstance<VarifySampled>()
            .map { it.attributedSample.sample }
            .filterIsInstance<Unshrinkable<*>>()
            .map(Unshrinkable<*>::value)
            .containsExactlyInAnyOrder(1, 1, 2, 2, 3, 3, "a", "a", "a", "b", "b", "b")
        expectThat(recorder.events.last()).isEqualTo(VariationsExhausted(attempts = 6))
    }

    @Test
    fun `The variation stage, when it reaches the attempt limit, finishes`() {
        val variations = mutableListOf<Int>()

        VariationStage(recorder.configuration(variationAttemptsLimit = 2)) {
            variations += oneOf(1, 2, 3)
        }

        expectThat(variations).hasSize(2)
        expectThat(variations.toSet()).hasSize(2)
        expectThat(recorder.events.last()).isEqualTo(VariationsAbandoned(attempts = 2))
    }

    @Test
    fun `The variation stage uses the first samples of a generator within the attempt limit`() {
        val variations = mutableListOf<Int>()

        VariationStage(recorder.configuration(variationAttemptsLimit = 5, randomizer = Randomizer.System(42))) {
            variations += some(int())
        }

        expectThat(variations).containsExactlyInAnyOrder(0, 1, -1, Int.MIN_VALUE, Int.MAX_VALUE)
    }

    @Test
    fun `The variation stage, when a test fails and its samples cannot be shrunk, fails with the encountered assertion error`() {
        val generator = from(1)

        expectThrows<AssertionError> {
            VariationStage(configuration) {
                val value = some(generator)
                fail("Fail with $value")
            }
        }.get { message }.isEqualTo("Fail with 1")
        expectThat(recorder.events)
            .filterIsInstance<VariationThrew>()
            .single()
            .and { get { generated }.containsExactly(AttributedSample(generator, Unshrinkable(1))) }
            .and { get { error.message }.isEqualTo("Fail with 1") }
    }

    @Test
    fun `The variation stage, when a test fails, stops generating further variations`() {
        expectThrows<AssertionError> {
            VariationStage(configuration) {
                val value = oneOf(1, 2, 3)
                fail("Fail with $value")
            }
        }
        expectThat(recorder.events).filterIsInstance<VariationInitiated>().hasSize(1)
        expectThat(recorder.events).filterIsInstance<VariationThrew>().single().get { generated }.hasSize(1)
    }

    @Test
    fun `The variation stage, when it can shrink the samples of a failed test, fails with the updated assertion error`() {
        val generator = Shrinkable(2)
        expectThrows<AssertionError> {
            VariationStage(configuration) {
                val value = some(generator)
                fail("Fail with $value")
            }
        }.get { message }.isEqualTo("Fail with 0")
    }

    @Test
    fun `The variation stage shrinks the samples of a test that fails with a non-assertion error`() {
        expectThrows<IllegalStateException> {
            VariationStage(configuration) {
                val value = some(Shrinkable(2))
                error("Fail with $value")
            }
        }.get { message }.isEqualTo("Fail with 0")
        expectThat(recorder.events)
            .filterIsInstance<VariationThrew>()
            .single()
            .get { error }
            .isA<IllegalStateException>()
    }

    @Test
    fun `The variation stage unwraps errors thrown by reflective invocations`() {
        expectThrows<AssertionError> {
            VariationStage(configuration) {
                oneOf(1)
                throw InvocationTargetException(AssertionError("Wrapped"))
            }
        }.get { message }.isEqualTo("Wrapped")
    }

    @Test
    fun `The variation stage, when every attempt is rejected by an assumption, aborts without shrinking`() {
        expectThrows<TestAbortedException> {
            VariationStage(configuration) {
                some(Shrinkable(2))
                throw TestAbortedException("Assumption failed")
            }
        }
        expectThat(recorder.events).none { isA<VariationThrew>() }
        expectThat(recorder.events).none { isA<ShrinkStarted>() }
    }

    @Test
    fun `The variation stage rethrows fatal errors without shrinking`() {
        expectThrows<OutOfMemoryError> {
            VariationStage(configuration) {
                some(Shrinkable(2))
                throw OutOfMemoryError("Simulated")
            }
        }
        expectThat(recorder.events).none { isA<VariationThrew>() }
        expectThat(recorder.events).none { isA<ShrinkStarted>() }
    }

    @Test
    fun `The variation stage, when a generator generates no samples, aborts without shrinking`() {
        expectThrows<VarifyConfigurationException> {
            VariationStage(configuration) {
                some(Shrinkable(2))
                some(from(emptyList<Int>()))
            }
        }
        expectThat(recorder.events).none { isA<VariationThrew>() }
        expectThat(recorder.events).none { isA<ShrinkStarted>() }
    }

    @Test
    fun `The variation stage, when the test function creates an invalid generator, aborts without shrinking`() {
        expectThrows<VarifyConfigurationException> {
            VariationStage(configuration) {
                some(Shrinkable(2))
                some(int(min = 1, max = 0))
            }
        }
        expectThat(recorder.events).none { isA<VariationThrew>() }
        expectThat(recorder.events).none { isA<ShrinkStarted>() }
    }

    @Test
    fun `The variation stage rethrows internal errors without shrinking`() {
        expectThrows<VarifyInternalException> {
            VariationStage(configuration) {
                some(Shrinkable(2))
                throw VarifyInternalException("broken")
            }
        }
        expectThat(recorder.events).none { isA<VariationThrew>() }
        expectThat(recorder.events).none { isA<ShrinkStarted>() }
    }

    @Test
    fun `The variation stage continues after attempts rejected by an assumption`() {
        val passed = mutableListOf<Int>()

        VariationStage(configuration) {
            val value = oneOf(1, 2, 3, 4)
            Assumptions.assumeTrue(value % 2 == 0)
            passed += value
        }

        expectThat(passed).containsExactlyInAnyOrder(2, 4)
        expectThat(recorder.events)
            .filterIsInstance<VariationRejected>()
            .map { it.generated.single().sample.value }
            .containsExactlyInAnyOrder(1, 3)
        expectThat(recorder.events.last()).isEqualTo(VariationsExhausted(attempts = 4))
    }

    @Test
    fun `The variation stage does not count rejected attempts towards the attempt limit`() {
        VariationStage(recorder.configuration(variationAttemptsLimit = 2)) {
            oneOf(1, 2)
            Assumptions.assumeTrue(oneOf(1, 2) == 2)
        }

        val rejected = recorder.events.filterIsInstance<VariationRejected>().size
        expectThat(rejected).isGreaterThan(0)
        expectThat(recorder.events.filterIsInstance<VariationPassed>()).hasSize(2)
        expectThat(recorder.events.last()).isEqualTo(VariationsAbandoned(attempts = 2 + rejected))
    }

    @Test
    fun `The variation stage, when it reaches the rejected attempts limit without a passed attempt, aborts`() {
        expectThrows<TestAbortedException> {
            VariationStage(recorder.configuration().copy(rejectedAttemptsLimit = 2)) {
                oneOf(1, 2, 3, 4, 5)
                Assumptions.assumeTrue(false)
            }
        }.get { message }.isEqualTo("Every attempt was rejected by an assumption (2 in total)")
        expectThat(recorder.events.last()).isEqualTo(VariationsRejectionLimitReached(attempts = 2))
    }

    // Like a generator built from a capturing lambda: its key doesn't include the captured offset.
    private class OffsetGenerator(private val offset: Int) : Generator<Int> {
        override val key = generatorKey<OffsetGenerator>()

        override fun generate(randomizer: Randomizer): Sequence<Sample<Int>> =
            sequenceOf(offset + 1, offset + 2).map(::Unshrinkable)
    }

    @Test
    fun `The variation stage generates samples per path, so generators with equal keys may depend on earlier samples`() {
        val variations = mutableListOf<Pair<Int, Int>>()

        VariationStage(configuration) {
            val offset = oneOf(0, 10)
            variations += offset to some(OffsetGenerator(offset))
        }

        expectThat(variations).containsExactlyInAnyOrder(0 to 1, 0 to 2, 10 to 11, 10 to 12)
    }

    @Test
    fun `The variation stage rejects a test function that requests a different generator after the same samples`() {
        var attempts = 0

        expectThrows<VarifyConfigurationException> {
            VariationStage(configuration) {
                oneOf(1)
                if (attempts++ == 0) oneOf("a", "b") else some(Shrinkable(1))
            }
        }.get { message }.isNotNull().endsWith("Test functions must be deterministic.")
    }

    @Test
    fun `The variation stage takes more samples from a generator when rejected attempts used up the first batch`() {
        val passed = mutableListOf<Int>()

        VariationStage(recorder.configuration(variationAttemptsLimit = 10)) {
            val value = some(int(0..1000))
            Assumptions.assumeTrue(value % 2 == 0)
            passed += value
        }

        // With TestRandomizer, the first batch of 10 samples (0, 1, 1000, then 999 down to 993) has only 5 even values.
        expectThat(passed).hasSize(10)
        expectThat(recorder.events.last()).isA<VariationsAbandoned>()
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `The variation stage, when a generator repeats a few values endlessly, becomes exhausted`() {
        val variations = mutableListOf<Int>()

        VariationStage(configuration) {
            variations += invoke(int().map { it % 3 })
        }

        expectThat(variations).isNotEmpty().and { get { distinct() }.isEqualTo(variations) }
        expectThat(variations).all { isIn(-2..2) }
        expectThat(recorder.events.last()).isA<VariationsExhausted>()
    }
}
