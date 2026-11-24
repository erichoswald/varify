package varify.generator

import varify.*
import varify.config.*

/**
 * Generates the samples of several [alternatives], choosing the alternative of each next sample randomly by its weight.
 *
 * Each alternative yields its samples in its own order, so its first (e.g. special) samples appear early. A sample shrinks
 * to the first sample of each earlier alternative before it shrinks within its own alternative, so put the simplest
 * alternative first.
 */
class MixedGenerator<V> internal constructor(
    private val alternatives: List<Generator<V>>,
    private val weights: List<Int>,
) : Generator<V> {
    init {
        checkConfiguration(alternatives.isNotEmpty()) { "mix needs at least one alternative" }
        checkConfiguration(weights.all { it > 0 }) { "weights $weights must be positive" }
    }

    override val key = generatorKey<MixedGenerator<*>>(alternatives.map { it.key }, weights)

    override fun generate(randomizer: Randomizer): Sequence<Sample<V>> =
        sequence {
            val iterators = alternatives.map { it.generate(randomizer).iterator() }
            val firstSamples = iterators.map { it.nextOrNull() }
            val availableSamples = firstSamples.toMutableList()
            val remainingAlternativeIndices = alternatives.indices
                .filter { firstSamples[it] != null }
                .toMutableList()
            while (remainingAlternativeIndices.isNotEmpty()) {
                val index = selectByWeight(remainingAlternativeIndices, randomizer)
                val sample = availableSamples[index]
                    ?.also { availableSamples[index] = null }
                    ?: iterators[index].nextOrNull()
                if (sample == null) {
                    remainingAlternativeIndices.remove(index)
                } else {
                    yield(MixedSample(index, sample, firstSamples))
                }
            }
        }

    private fun selectByWeight(indices: List<Int>, randomizer: Randomizer): Int {
        var selected = randomizer.selectFrom(0..<indices.sumOf { weights[it] })
        for (index in indices) {
            selected -= weights[index]
            if (selected < 0) return index
        }
        throw VarifyInternalException("Selection $selected exceeds the weights of $indices")
    }

    override fun rebuild(sample: Sample<*>): Sample<V> {
        val mixed = sample as MixedGenerator<*>.MixedSample
        val shrinkTargets = mixed.shrinkTargets.mapIndexed { index, target ->
            target?.let { alternatives[index].rebuild(it) }
        }
        return MixedSample(mixed.index, alternatives[mixed.index].rebuild(mixed.source), shrinkTargets)
    }

    private inner class MixedSample(
        val index: Int,
        val source: Sample<V>,
        val shrinkTargets: List<Sample<V>?>,
    ) : Sample<V>() {
        override val value: V = source.value

        override fun shrink(): List<Sample<V>> {
            val earlierSamples = (0..<index).mapNotNull { earlierIndex ->
                shrinkTargets[earlierIndex]?.let { MixedSample(earlierIndex, it, shrinkTargets) }
            }
            return earlierSamples + source.shrink().map { MixedSample(index, it, shrinkTargets) }
        }
    }
}

/** Creates generators that mix several alternatives, e.g. `mix(codePoint.ascii(), codePoint.bmp())` or `mix.weighted(a to 9, b to 1)`. */
@Suppress("ClassName")
object mix {
    /** Generates the samples of [alternatives], each alternative with the same chance. */
    operator fun <V> invoke(vararg alternatives: Generator<V>): MixedGenerator<V> =
        MixedGenerator(alternatives.toList(), alternatives.map { 1 })

    /** Generates the samples of alternatives, each with a chance proportional to its weight, e.g. `a to 9, b to 1`. */
    fun <V> weighted(vararg alternatives: Pair<Generator<V>, Int>): MixedGenerator<V> =
        MixedGenerator(alternatives.map { it.first }, alternatives.map { it.second })
}

private fun <T> Iterator<T>.nextOrNull(): T? =
    takeIf { hasNext() }?.next()
