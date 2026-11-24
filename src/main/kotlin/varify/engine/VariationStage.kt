package varify.engine

import org.opentest4j.*
import varify.*
import varify.config.*
import varify.event.*
import varify.generator.*
import varify.syntax.*

class VariationStage private constructor(
    override val configuration: VarifyConfiguration,
    private val testClass: Class<*>,
) : VarifyStage {

    // The variation tree: the path from the root to a leaf is the sequence of samples of one attempt.
    private val root = RootNode()
    private var path = mutableListOf<SampleNode>()

    fun variate(test: VarifyContext.() -> Unit) {
        var attempts = 0
        var rejected = 0
        while (true) {
            publish(VariationInitiated(++attempts))
            path = mutableListOf()

            when (val error = attempt(test)) {
                null -> {
                    // The test passed without exception.
                    publish(VariationPassed(attempts, collectGeneratedSamples()))
                }

                is TestAbortedException -> {
                    // An assumption was violated. We continue without counting the attempt for the limit.
                    rejected++
                    publish(VariationRejected(attempts, collectGeneratedSamples(), error))
                }

                else -> {
                    // The test failed with an exception, try to shrink this variation's samples.
                    fail(error, test)
                }
            }

            if (attempts - rejected >= configuration.variationAttemptsLimit) {
                publish(VariationsAbandoned(attempts))
                return
            }
            if (rejected >= configuration.rejectedAttemptsLimit) {
                publish(VariationsRejectionLimitReached(attempts))
                return requirePassedAttempt(attempts, rejected)
            }

            // Prune exhausted nodes, i.e. nodes whose sub-paths have all been used already (passed or rejected) and
            // whose generator has no further samples. Only rejected attempts can use up a node's first batch of samples.
            while (currentNode().available.isEmpty() && !currentNode().takeSamples()) {
                // The current node is exhausted, i.e. all the samples its generator can provide have been used.
                if (path.isEmpty()) {
                    // We have exhausted all nodes, i.e. all the samples of all generators have been used.
                    publish(VariationsExhausted(attempts))
                    return requirePassedAttempt(attempts, rejected)
                }

                // Otherwise we focus on the parent node and remove the exhausted child.
                val exhausted = path.removeLast()
                currentNode().available.remove(exhausted)
            }
        }
    }

    private fun requirePassedAttempt(attempts: Int, rejected: Int) {
        if (rejected == attempts) {
            throw TestAbortedException("Every attempt was rejected by an assumption ($attempts in total)")
        }
    }

    private fun fail(error: Throwable, test: VarifyContext.() -> Unit): Nothing {
        val generatedSamples = collectGeneratedSamples()
        publish(VariationThrew(generatedSamples, error))

        // The shrinking stage may terminate with a different error if it finds a smaller input.
        ShrinkingStage.shrink(generatedSamples, error, configuration, testClass, test)

        // Otherwise we throw the original error.
        throw error
    }

    private fun currentNode(): VariationNode =
        path.lastOrNull() ?: root

    private fun collectGeneratedSamples(): List<AttributedSample<*>> =
        path.map(SampleNode::attributed)

    override fun <V> invoke(generator: Generator<V>): V {
        val node = currentNode()
        val source = node.source
        if (source == null) {
            // On the first request for the current node, generate its samples and use them as sub-paths of this node.
            node.source = SampleSource(generator, configuration.randomizer)
            node.takeSamples()
            checkConfiguration(node.available.isNotEmpty()) { "Generator $generator generated empty sample sequence" }
        } else {
            checkConfiguration(source.generator.key == generator.key) {
                "The test function requested ${generator.key} after receiving the same samples as in a previous " +
                    "attempt that requested ${source.generator.key} instead. Test functions must be deterministic."
            }
        }

        checkInternal(node.available.isNotEmpty()) { "No available path for generator $generator" }
        val selected = configuration.randomizer.selectFrom(node.available)
        publish(VarifySampled(path.size, selected.attributed))
        path += selected

        @Suppress("UNCHECKED_CAST")
        return selected.attributed.sample.value as V
    }

    // Takes the next batch of samples from the generator of the node's next request, returns false if there are none.
    private fun VariationNode.takeSamples(): Boolean {
        val batch = source?.take(configuration.variationAttemptsLimit).orEmpty()
        available += batch
        return batch.isNotEmpty()
    }

    // The generator of a node's next request with its remaining distinct samples.
    private class SampleSource<V>(val generator: Generator<V>, randomizer: Randomizer) {
        private val samples = generator.generate(randomizer).distinctUntilExhausted().iterator()

        fun take(count: Int): List<SampleNode> =
            samples.asSequence().take(count).map { SampleNode(AttributedSample(generator, it)) }.toList()
    }

    private sealed class VariationNode {
        // The generator that the test function requests next with its samples, null until it does.
        var source: SampleSource<*>? = null

        // The samples of the next request whose sub-paths haven't all been used yet.
        val available = mutableListOf<SampleNode>()
    }

    private class RootNode : VariationNode()

    private class SampleNode(val attributed: AttributedSample<*>) : VariationNode()

    companion object {
        operator fun invoke(
            configuration: VarifyConfiguration,
            testClass: Class<*>? = null,
            test: VarifyContext.() -> Unit,
        ) {
            VariationStage(configuration, testClass ?: test.javaClass).variate(test)
        }
    }
}
