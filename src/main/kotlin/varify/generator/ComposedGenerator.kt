@file:Suppress("unused")

package varify.generator

import varify.*
import varify.config.*

/**
 * Generates combinations of the samples of [sources], combined by [combine]. The samples shrink through their source
 * samples, one source at a time.
 *
 * Generators yield their most interesting samples first (e.g. special values), so every other combination is ordered by
 * the sum of its samples' positions in the source sequences: first the combination of all first samples, then those with
 * one second sample, then those with one third or two second samples, and so on. For two sources, this traverses the
 * matrix of combinations diagonally.
 *
 * With several sources, the diagonal only slowly reaches later (e.g. random) samples: its first 100 combinations of five
 * sources only use their first five samples. So the other combinations take a random position for each source,
 * from a window that grows with the number of combinations yielded so far. Each combination is yielded only once, and
 * finite sources end with the last diagonal combination. Samples of each source are only generated when needed, so
 * infinite sources work, too.
 *
 * The key identifies [combine] by its class, like [MappedGenerator]. The function takes one parameter per source, typed by
 * the `compose` functions. Within the generator, the parameter types are erased.
 */
class ComposedGenerator<V> internal constructor(
    private val sources: List<Generator<*>>,
    private val combine: Function<V>,
) : Generator<V> {
    override val key = generatorKey<ComposedGenerator<*>>(sources.map { it.key }, combine.javaClass)

    override fun generate(randomizer: Randomizer): Sequence<Sample<V>> =
        sequence {
            val sourceSamples = sources.map { SourceSamples(it.generate(randomizer).iterator()) }
            val yielded = mutableSetOf<List<Int>>()
            for (diagonal in diagonalPositions(sourceSamples)) {
                if (yielded.add(diagonal)) {
                    yield(ComposedSample(sourceSamples, diagonal))
                }
                // A window of twice the combinations so far lets each source advance about as fast as it would alone.
                val random = sourceSamples.map { it.selectPosition(randomizer, window = 2 * yielded.size) }
                if (yielded.add(random)) {
                    yield(ComposedSample(sourceSamples, random))
                }
            }
        }

    // The reachable sums have no gaps, so the first level without combinations is the end of finite sources.
    private fun diagonalPositions(sourceSamples: List<SourceSamples>): Sequence<List<Int>> =
        sequence {
            var level = 0
            while (yieldDiagonalPositions(sourceSamples, level, emptyList())) {
                level++
            }
        }

    // Yields the positions that start with prefix and sum up to level. Returns whether there were any.
    private suspend fun SequenceScope<List<Int>>.yieldDiagonalPositions(
        sourceSamples: List<SourceSamples>,
        level: Int,
        prefix: List<Int>,
    ): Boolean {
        val source = sourceSamples[prefix.size]
        if (prefix.size == sourceSamples.lastIndex) {
            if (source.getOrNull(level) == null) return false
            yield(prefix + level)
            return true
        }
        var yielded = false
        for (position in 0..level) {
            if (source.getOrNull(position) == null) break
            yielded = yieldDiagonalPositions(sourceSamples, level - position, prefix + position) || yielded
        }
        return yielded
    }

    // The samples of a source generated so far, generating more on demand.
    private class SourceSamples(private val iterator: Iterator<Sample<*>>) {
        private val samples = mutableListOf<Sample<*>>()

        operator fun get(position: Int): Sample<*> =
            samples[position]

        fun getOrNull(position: Int): Sample<*>? {
            while (samples.size <= position && iterator.hasNext()) {
                samples += iterator.next()
            }
            return samples.getOrNull(position)
        }

        // Only called after the diagonal found the first sample, so there is at least one position to select.
        fun selectPosition(randomizer: Randomizer, window: Int): Int {
            getOrNull(window - 1)
            return randomizer.selectFrom(0..<minOf(window, samples.size))
        }
    }

    override fun rebuild(sample: Sample<*>): Sample<V> =
        (sample as ComposedGenerator<*>.ComposedSample).let { composedSample ->
            val rebuiltSources = composedSample.sources.mapIndexed { index, source -> sources[index].rebuild(source) }
            ComposedSample(rebuiltSources)
        }

    private inner class ComposedSample(val sources: List<Sample<*>>) : Sample<V>() {
        constructor(sourceSamples: List<SourceSamples>, positions: List<Int>) :
            this(positions.mapIndexed { index, position -> sourceSamples[index][position] })

        override val value: V = combine(sources.map { it.value })

        // Different sources can combine to the same value, which isn't smaller.
        override fun shrink(): List<Sample<V>> =
            sources.flatMapIndexed { index, source ->
                source.shrink().map { shrunk ->
                    ComposedSample(sources.toMutableList().apply { set(index, shrunk) })
                }
            }.filter { it.value != value }
    }

    @Suppress("UNCHECKED_CAST")
    private fun combine(values: List<Any?>): V =
        when (values.size) {
            2 -> (combine as (Any?, Any?) -> V)(values[0], values[1])
            3 -> (combine as (Any?, Any?, Any?) -> V)(values[0], values[1], values[2])
            4 -> (combine as (Any?, Any?, Any?, Any?) -> V)(values[0], values[1], values[2], values[3])
            5 -> (combine as (Any?, Any?, Any?, Any?, Any?) -> V)(values[0], values[1], values[2], values[3], values[4])
            else -> throw VarifyInternalException("Unsupported number of sources: ${values.size}")
        }
}

fun <A, B, V> compose(
    a: Generator<A>,
    b: Generator<B>,
    combine: (A, B) -> V,
): Generator<V> =
    ComposedGenerator(listOf(a, b), combine)

fun <A, B, C, V> compose(
    a: Generator<A>,
    b: Generator<B>,
    c: Generator<C>,
    combine: (A, B, C) -> V,
): Generator<V> =
    ComposedGenerator(listOf(a, b, c), combine)

fun <A, B, C, D, V> compose(
    a: Generator<A>,
    b: Generator<B>,
    c: Generator<C>,
    d: Generator<D>,
    combine: (A, B, C, D) -> V,
): Generator<V> =
    ComposedGenerator(listOf(a, b, c, d), combine)

fun <A, B, C, D, E, V> compose(
    a: Generator<A>,
    b: Generator<B>,
    c: Generator<C>,
    d: Generator<D>,
    e: Generator<E>,
    combine: (A, B, C, D, E) -> V,
): Generator<V> =
    ComposedGenerator(listOf(a, b, c, d, e), combine)

fun <A, B> pair(
    a: Generator<A>,
    b: Generator<B>,
): Generator<Pair<A, B>> =
    compose(a, b, ::Pair)
