package varify.engine

import org.junit.jupiter.api.*
import org.opentest4j.*
import strikt.api.*
import strikt.assertions.*
import varify.VarifyInternalException
import varify.config.*
import varify.event.*
import varify.generator.*
import varify.generator.primitive.*
import varify.syntax.*

class ShrinkingStageTest {

    // Same type as thrown by `fail`, suppress stack frame to compare only exception class.
    private val originalError = AssertionFailedError("Original failure").apply { stackTrace = emptyArray() }

    data object DummyGenerator : Generator<Int> {
        override val key = generatorKey<DummyGenerator>()

        override fun generate(randomizer: Randomizer): Sequence<Sample<Int>> {
            fail("Dummy generate must not be called")
        }
    }

    @Test
    fun `The shrinking stage cannot shrink an empty sample list`() {
        val recorder = VarifyEventRecorder()

        ShrinkingStage.shrink(emptyList(), originalError, recorder.configuration()) {
            fail("Unexpected call to test function: no samples available")
        }

        expectThat(recorder).hasExactlyEvents(
            isShrinkingStageExhausted(attempts = 0),
        )
    }

    @Test
    fun `The shrinking stage completes unsuccessfully when it reaches the attempt limit`() {
        val samples = listOf(bisecting(10))
        val recorder = VarifyEventRecorder()
        val configuration = recorder.configuration(shrinkAttemptsLimit = 2)

        ShrinkingStage.shrink(samples, originalError, configuration, successfulTest(samples.size))

        expectThat(recorder).hasExactlyEvents(
            isShrinkStarted(attempt = 1, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 0),
            isShrinkPassed(attempt = 1, shrinkIndex = 0),
            isShrinkStarted(attempt = 2, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 5),
            isShrinkPassed(attempt = 2, shrinkIndex = 0),
            isShrinkingAttemptLimitReached(attempts = 2),
        )
    }

    @Test
    fun `The shrinking stage completes successfully when it reaches the attempt limit but was able to shrink`() {
        val samples = listOf(decrementing(5))
        val recorder = VarifyEventRecorder()
        val configuration = recorder.configuration(shrinkAttemptsLimit = 2)

        expectCatching { ShrinkingStage.shrink(samples, originalError, configuration, failingTest(samples.size)) }

        expectThat(recorder).hasExactlyEvents(
            isShrinkStarted(attempt = 1, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 4),
            isShrinkThrew(attempt = 1, shrinkIndex = 0),
            isShrinkStarted(attempt = 2, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 3),
            isShrinkThrew(attempt = 2, shrinkIndex = 0),
            isShrinkingAttemptLimitReached(attempts = 2),
        )
    }

    @Test
    fun `The shrinking stage is exhausted when it runs out of candidates`() {
        val samples = listOf(decrementing(1))
        val recorder = VarifyEventRecorder()
        val configuration = recorder.configuration()

        ShrinkingStage.shrink(samples, originalError, configuration, successfulTest(samples.size))

        expectThat(recorder).hasExactlyEvents(
            isShrinkStarted(attempt = 1, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 0),
            isShrinkPassed(attempt = 1, shrinkIndex = 0),
            isShrinkingStageExhausted(attempts = 1),
        )
    }

    @Test
    fun `The shrinking stage completes successfully when it runs out of candidates but was able to shrink`() {
        val samples = listOf(decrementing(1))
        val recorder = VarifyEventRecorder()
        val configuration = recorder.configuration()

        expectCatching { ShrinkingStage.shrink(samples, originalError, configuration, failingTest(samples.size)) }

        expectThat(recorder).hasExactlyEvents(
            isShrinkStarted(attempt = 1, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 0),
            isShrinkThrew(attempt = 1, shrinkIndex = 0),
            isShrinkingStageExhausted(attempts = 1),
        )
    }

    @Test
    fun `The shrinking stage shrinks multiple samples from first to last`() {
        val samples = listOf(decrementing(2), unshrinkable(3), decrementing(1))
        val recorder = VarifyEventRecorder()
        val configuration = recorder.configuration()

        expectCatching { ShrinkingStage.shrink(samples, originalError, configuration, failingTest(samples.size)) }
            .isFailure()

        expectThat(recorder).hasExactlyEvents(
            isShrinkStarted(attempt = 1, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 1),
            isVarifySampled(sampleIndex = 1, value = 3),
            isVarifySampled(sampleIndex = 2, value = 1),
            isShrinkThrew(attempt = 1, shrinkIndex = 0),
            isShrinkStarted(attempt = 2, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 0),
            isVarifySampled(sampleIndex = 1, value = 3),
            isVarifySampled(sampleIndex = 2, value = 1),
            isShrinkThrew(attempt = 2, shrinkIndex = 0),
            isShrinkStarted(attempt = 3, shrinkIndex = 2),
            isVarifySampled(sampleIndex = 0, value = 0),
            isVarifySampled(sampleIndex = 1, value = 3),
            isVarifySampled(sampleIndex = 2, value = 0),
            isShrinkThrew(attempt = 3, shrinkIndex = 2),
            isShrinkingStageExhausted(attempts = 3),
        )
    }

    @Test
    fun `The shrinking stage explores the branch of a candidate that changes the generators of later samples`() {
        val samples = listOf(generated(int(1..10), 8), generated(int(0..<8), 6))
        val recorder = VarifyEventRecorder()

        // Index 6 of size 8 fails, but the index samples of a smaller size start with 0 and 1, so it needs several attempts.
        expectThrows<AssertionFailedError> {
            ShrinkingStage.shrink(samples, originalError, recorder.configuration()) {
                val size = invoke(int(1..10))
                val index = invoke(int(0..<size))
                if (index >= 5) fail("index $index of size $size")
            }
        }.get { message }.isEqualTo("index 5 of size 6")
    }

    @Test
    fun `The shrinking stage rebuilds samples of lambdas that capture earlier samples with their current values`() {
        fun scaled(factor: Int) = int(0..100).map { it * factor }
        // Start shrinking from a factor of 8 and a scaled sample of 24 (from an implicit source sample 3).
        val samples = listOf(
            generated(int(1..10), 8),
            scaled(8).let { AttributedSample(it, it.sampleOf(24)) },
        )
        val recorder = VarifyEventRecorder()

        // Reusing the stale sample would accept factor 1 (shrunk) with 24 (stale).
        // Rebuilt the first scaled sample to fail is factor 7 (shrunk) with 21 (rebuilt).
        expectThrows<AssertionFailedError> {
            ShrinkingStage.shrink(samples, originalError, recorder.configuration()) {
                val factor = invoke(int(1..10))
                val scaled = invoke(scaled(factor))
                if (scaled >= 20) fail("factor $factor, scaled $scaled")
            }
        }.get { message }.isEqualTo("factor 7, scaled 21")
    }

    @Test
    fun `The shrinking stage drops samples that the test no longer requests`() {
        val samples = listOf(generated(int(0..5), 4)) + List(4) { generated(int(0..100), 50) }
        val recorder = VarifyEventRecorder()

        expectThrows<AssertionFailedError> {
            ShrinkingStage.shrink(samples, originalError, recorder.configuration()) {
                val values = List(invoke(int(0..5))) { invoke(int(0..100)) }
                if (values.size >= 2) fail("too many: $values")
            }
        }.get { message }.isEqualTo("too many: [0, 0]")

        expectThat(recorder.events.filterIsInstance<ShrinkThrew>().last().attributedSamples.map { it.sample.value })
            .containsExactly(2, 0, 0)
    }

    @Test
    fun `The shrinking stage limits the attempts to explore the branch of a candidate`() {
        val samples = listOf(generated(int(0..1), 1), generated(int(0..101), 50))
        val recorder = VarifyEventRecorder()

        expectCatching {
            ShrinkingStage.shrink(samples, originalError, recorder.configuration()) {
                val flag = invoke(int(0..1))
                invoke(int(0..100 + flag))
                if (flag == 1) fail("flag set")
            }
        }.isFailure()

        expectThat(recorder.events.filterIsInstance<ShrinkPassed>().filter { it.shrinkIndex == 0 }).hasSize(10)
    }

    @Test
    fun `The shrinking stage ignores a candidate when its branch runs out of samples`() {
        val samples = listOf(bisecting(2), AttributedSample(from(0, 1), Unshrinkable(1)))
        val recorder = VarifyEventRecorder()

        ShrinkingStage.shrink(samples, originalError, recorder.configuration()) {
            val value = invoke(DummyGenerator)
            invoke(from(List(value) { it }))
        }

        expectThat(recorder).hasExactlyEvents(
            isShrinkStarted(attempt = 1, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 0),
            isShrinkIgnored(attempt = 1, shrinkIndex = 0),
            isShrinkStarted(attempt = 2, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 1),
            isVarifySampled(sampleIndex = 1, value = 0),
            isShrinkPassed(attempt = 2, shrinkIndex = 0),
            isShrinkStarted(attempt = 3, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 1),
            isShrinkIgnored(attempt = 3, shrinkIndex = 0),
            isShrinkingStageExhausted(attempts = 3),
        )
    }

    @Test
    fun `The shrinking stage does not accept candidates that fail with a different error type`() {
        val samples = listOf(bisecting(2))
        val recorder = VarifyEventRecorder()
        val configuration = recorder.configuration()

        ShrinkingStage.shrink(samples, originalError, configuration, throwingTest(samples.size) { IllegalStateException() })

        expectThat(recorder).hasExactlyEvents(
            isShrinkStarted(attempt = 1, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 0),
            isShrinkDiverged(attempt = 1, shrinkIndex = 0),
            isShrinkStarted(attempt = 2, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 1),
            isShrinkDiverged(attempt = 2, shrinkIndex = 0),
            isShrinkingStageExhausted(attempts = 2),
        )
    }

    @Test
    fun `The shrinking stage ignores candidates for which an assumption fails`() {
        val samples = listOf(bisecting(2))
        val recorder = VarifyEventRecorder()
        val configuration = recorder.configuration()

        ShrinkingStage.shrink(samples, originalError, configuration, throwingTest(samples.size) { TestAbortedException() })

        expectThat(recorder).hasExactlyEvents(
            isShrinkStarted(attempt = 1, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 0),
            isShrinkIgnored(attempt = 1, shrinkIndex = 0),
            isShrinkStarted(attempt = 2, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 1),
            isShrinkIgnored(attempt = 2, shrinkIndex = 0),
            isShrinkingStageExhausted(attempts = 2),
        )
    }

    @Test
    fun `The shrinking stage finds the smallest failing value between a failing and a passing candidate`() {
        val samples = listOf(bisecting(896))
        val recorder = VarifyEventRecorder()

        expectThrows<AssertionFailedError> {
            ShrinkingStage.shrink(samples, originalError, recorder.configuration(), failingIfNoneBelowThreshold(samples.size, 300))
        }.get { message }.isEqualTo("too big: [300]")
    }

    @Test
    fun `The shrinking stage shrinks later samples together with the smallest failing earlier samples`() {
        val samples = listOf(bisecting(896), bisecting(896))
        val recorder = VarifyEventRecorder()

        expectThrows<AssertionFailedError> {
            ShrinkingStage.shrink(samples, originalError, recorder.configuration(), failingIfNoneBelowThreshold(samples.size, 300))
        }.get { message }.isEqualTo("too big: [300, 300]")
    }

    @Test
    fun `The shrinking stage aborts on internal errors instead of treating them as diverging failures`() {
        val samples = listOf(bisecting(2))
        val recorder = VarifyEventRecorder()
        val test = throwingTest(samples.size) { VarifyInternalException("broken") }

        expectThrows<VarifyInternalException> {
            ShrinkingStage.shrink(samples, originalError, recorder.configuration(), test)
        }

        expectThat(recorder).hasExactlyEvents(
            isShrinkStarted(attempt = 1, shrinkIndex = 0),
            isVarifySampled(sampleIndex = 0, value = 0),
        )
    }

    private fun bisecting(value: Int): AttributedSample<Int> =
        AttributedSample(DummyGenerator, IntSample(value, BisectingIntShrinker(0)))

    private fun generated(generator: IntGenerator, value: Int): AttributedSample<Int> =
        AttributedSample(generator, IntSample(value, generator.shrinker))

    private fun <V> Generator<V>.sampleOf(value: V): Sample<V> =
        allSamples().first { it.value == value }

    private fun decrementing(value: Int): AttributedSample<Int> =
        AttributedSample(DummyGenerator, ShrinkableSample(value))

    @Suppress("SameParameterValue")
    private fun unshrinkable(value: Int): AttributedSample<Int> =
        AttributedSample(DummyGenerator, Unshrinkable(value))

    private fun successfulTest(sampleCount: Int): VarifyContext.() -> Unit =
        {
            repeat(sampleCount) {
                invoke(DummyGenerator)
            }
        }

    private fun failingTest(sampleCount: Int): VarifyContext.() -> Unit =
        {
            repeat(sampleCount) {
                invoke(DummyGenerator)
            }
            fail("Failing after $sampleCount samples")
        }

    private fun failingIfNoneBelowThreshold(
        sampleCount: Int,
        @Suppress("SameParameterValue") threshold: Int,
    ): VarifyContext.() -> Unit =
        {
            val values = List(sampleCount) { invoke(DummyGenerator) }
            if (values.all { it >= threshold }) fail("too big: $values")
        }

    private fun throwingTest(sampleCount: Int, error: () -> Throwable): VarifyContext.() -> Unit =
        {
            repeat(sampleCount) {
                invoke(DummyGenerator)
            }
            throw error()
        }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `The shrinking stage ignores a candidate when its branch repeats a few values endlessly`() {
        val samples = listOf(generated(int(0..1), 1))
        val recorder = VarifyEventRecorder()

        ShrinkingStage.shrink(samples, originalError, recorder.configuration()) {
            // The candidate 0 branches into a generator with only two values, so its third attempt finds none.
            if (invoke(int(0..1)) == 0) invoke(int().map { it % 2 == 0 }) else fail("flag set")
        }

        expectThat(recorder.events.filterIsInstance<ShrinkPassed>()).hasSize(2)
        expectThat(recorder.events.filterIsInstance<ShrinkIgnored>()).hasSize(1)
    }
}
