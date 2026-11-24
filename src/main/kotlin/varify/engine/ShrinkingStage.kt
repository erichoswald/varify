package varify.engine

import org.opentest4j.*
import varify.*
import varify.config.*
import varify.event.*
import varify.generator.*
import varify.syntax.*

// The number of attempts per candidate that leads into a different branch of the variation tree.
private const val BRANCH_ATTEMPTS_LIMIT = 10

class ShrinkingStage private constructor(
    samples: List<AttributedSample<*>>,
    originalError: Throwable,
    override val configuration: VarifyConfiguration,
    private val testClass: Class<*>,
    private val test: VarifyContext.() -> Unit,
) : VarifyStage {

    private val originalOrigin = FailureOrigin.of(originalError, testClass)

    private class Shrinker<V>(attributedSample: AttributedSample<V>) {
        val generator = attributedSample.generator

        private var failing = attributedSample.sample // The smallest sample known to fail the test.
        private var candidate: Sample<V>? = null // The candidate tried in the current attempt (if any).
        private val untestedCandidates = ArrayDeque(failing.shrink())
        private val testedValues = mutableSetOf(failing.value)

        /** Rebuilds the current sample with the [generator] requested in this attempt (with the same key). */
        fun rebuild(generator: Generator<V>): AttributedSample<V> =
            AttributedSample(generator, generator.rebuild(candidate ?: failing))

        /** Returns whether selecting another candidate was successful. */
        fun selectNextUntestedCandidate(): Boolean {
            candidate = null
            while (untestedCandidates.isNotEmpty()) {
                val next = untestedCandidates.removeFirst()
                if (testedValues.add(next.value)) {
                    candidate = next
                    return true
                }
            }
            return false
        }

        /** Makes the current candidate, as rebuilt in the attempt, become the new smallest failing sample. */
        @Suppress("UNCHECKED_CAST")
        fun promoteCurrent(rebuilt: AttributedSample<*>) {
            checkInternalNotNull(candidate) { "No candidate to promote" }
            failing = rebuilt.sample as Sample<V>
            candidate = null
            untestedCandidates.clear()
            untestedCandidates += failing.shrink()
        }
    }

    /**
     * Explores a branch in the variation tree that was not covered in the original failure, i.e., the test
     * requests a sample from a different generator than in the failing test. During shrinking, we spend a few
     * attempts per candidate exploring if the branch leads to the same failure, in which case it replaces the
     * original variation tree.
     */
    private class BranchExplorer(private val randomizer: Randomizer) {
        private val sources = mutableMapOf<Int, SampleSource<*>>()
        private var attempts = 0
        private var branched = false // Whether the current attempt left the path of the failing samples.
        private var exhausted = false // Whether a generator of the branch ran out of samples.

        fun startAttempt() {
            attempts++
            branched = false
        }

        /** Returns whether another attempt with the same candidate could find failing samples in its branch. */
        fun canRetry(): Boolean =
            branched && !exhausted && attempts < BRANCH_ATTEMPTS_LIMIT

        @Suppress("UNCHECKED_CAST")
        fun <V> explore(sampleIndex: Int, generator: Generator<V>): Shrinker<V> {
            branched = true
            val source = sources[sampleIndex]
                ?.takeIf { it.generator.key == generator.key } as SampleSource<V>?
                ?: SampleSource(generator, randomizer).also { sources[sampleIndex] = it }
            return source.next() ?: run {
                exhausted = true
                throw IgnoreShrinkingAttempt()
            }
        }
    }

    private class SampleSource<V>(val generator: Generator<V>, randomizer: Randomizer) {
        private val samples = generator.generate(randomizer).distinctUntilExhausted().iterator()

        fun next(): Shrinker<V>? =
            if (samples.hasNext()) Shrinker(AttributedSample(generator, samples.next())) else null
    }

    private class IgnoreShrinkingAttempt : Exception()

    // The shrinkers of the smallest failing samples, and the index of the focus shrinker whose candidate is being tried.
    private var shrinkers: List<Shrinker<*>> = samples.map { Shrinker(it) }
    private var shrinkerIndex = 0

    // Samples and their shrinkers for tracking samples used in the current attempt.
    private data class AttemptSample(val shrinker: Shrinker<*>, val attributed: AttributedSample<*>)
    private var attemptSamples = mutableListOf<AttemptSample>()

    private var branchExplorer = BranchExplorer(configuration.randomizer)

    private fun shrink() {
        var lastError: Throwable? = null
        var attempts = 0
        while (attempts < configuration.shrinkAttemptsLimit && selectNextCandidate()) {
            branchExplorer = BranchExplorer(configuration.randomizer)
            do {
                attempts++
                val error = shrinkAttempt(attempts)
                if (error != null) {
                    lastError = error
                }
                // Retry the candidate with other samples in its branch while it doesn't fail the same way.
            } while (error == null && branchExplorer.canRetry() && attempts < configuration.shrinkAttemptsLimit)
        }

        if (selectNextCandidate()) {
            publish(ShrinkingAbandoned(attempts))
        } else {
            publish(ShrinkingExhausted(attempts))
        }
        if (lastError != null) {
            throw lastError
        }
    }

    /** Returns the error if the attempt failed the same way as the original sample, i.e. the candidate was promoted. */
    private fun shrinkAttempt(attempt: Int): Throwable? {
        attemptSamples = mutableListOf()
        branchExplorer.startAttempt()
        publish(ShrinkStarted(attempt, shrinkerIndex))
        val error = attempt(test)
        when {
            error == null -> {
                publish(ShrinkPassed(attempt, shrinkerIndex))
            }

            error is IgnoreShrinkingAttempt || error is TestAbortedException -> {
                // Attempt unsuccessful because the candidate's branch ran out of samples or an assumption failed.
                publish(ShrinkIgnored(attempt, shrinkerIndex))
            }

            !FailureOrigin.of(error, testClass).matches(originalOrigin) -> {
                // Attempt unsuccessful because the smaller input fails for a different reason.
                publish(ShrinkDiverged(attempt, shrinkerIndex, error))
            }

            else -> {
                // Attempt successful, continue shrinking from the samples of this attempt.
                publish(ShrinkThrew(attempt, shrinkerIndex, attemptSamples.map(AttemptSample::attributed), error))
                promoteAttempt()
                return error
            }
        }
        return null
    }

    // Samples the attempt didn't request are dropped, and samples from the branch are shrunk after the current shrinker.
    private fun promoteAttempt() {
        val current = shrinkers[shrinkerIndex]
        shrinkers = attemptSamples.mapIndexed { index, (shrinker, attributed) ->
            when {
                index < shrinkerIndex -> shrinker // Done shrinking.
                index == shrinkerIndex && shrinker === current -> shrinker.apply { promoteCurrent(attributed) }
                else -> Shrinker(attributed) // Not shrunk yet, so start from the rebuilt sample.
            }
        }
    }

    private fun selectNextCandidate(): Boolean {
        while (shrinkerIndex < shrinkers.size) {
            if (shrinkers[shrinkerIndex].selectNextUntestedCandidate()) {
                return true // Current shrinker is still good for more candidates.
            }
            shrinkerIndex++ // Current shrinker is out of candidates, try next one.
        }
        return false // Completely run out of candidates.
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V> invoke(generator: Generator<V>): V {
        val sampleIndex = attemptSamples.size
        val shrinker = shrinkers.getOrNull(sampleIndex)
            ?.takeIf { it.generator.key == generator.key } as Shrinker<V>?
            ?: branchExplorer.explore(sampleIndex, generator)

        val attributed = shrinker.rebuild(generator)
        publish(VarifySampled(sampleIndex, attributed))
        attemptSamples += AttemptSample(shrinker, attributed)
        return attributed.sample.value
    }

    companion object {
        fun shrink(
            samples: List<AttributedSample<*>>,
            originalError: Throwable,
            configuration: VarifyConfiguration,
            test: VarifyContext.() -> Unit,
        ) {
            shrink(samples, originalError, configuration, test.javaClass, test)
        }

        fun shrink(
            samples: List<AttributedSample<*>>,
            originalError: Throwable,
            configuration: VarifyConfiguration,
            testClass: Class<*>,
            test: VarifyContext.() -> Unit,
        ) {
            ShrinkingStage(samples, originalError, configuration, testClass, test).shrink()
        }
    }
}
