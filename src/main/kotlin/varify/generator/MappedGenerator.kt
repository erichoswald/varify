package varify.generator

import varify.config.*

/**
 * Generates the samples of [source] transformed by [transform]. The samples shrink through their source samples.
 *
 * The key identifies [transform] by its class, i.e. its call site: a lambda that captures values is a new instance in
 * each attempt, but always has the same class. Captured values are not part of the key, so when a captured value is an
 * earlier sample that shrinks, the shrinking stage rebuilds the sample from its source sample with the current [transform]
 * instead of trying other source samples. To have those tried, too, pass such dependencies to the source generator instead (e.g.
 * `int(0..<size).map { ... }`).
 *
 * Library functions that capture their own parameters in [transform] (e.g. a separator) pass them as [keyProperties], so
 * that generators with different parameters have different keys.
 */
class MappedGenerator<S, V> internal constructor(
    private val source: Generator<S>,
    private val keyProperties: List<Any>,
    private val transform: (S) -> V,
) : Generator<V> {
    constructor(source: Generator<S>, transform: (S) -> V) : this(source, emptyList(), transform)

    override val key = generatorKey<MappedGenerator<*, *>>(source.key, transform.javaClass, keyProperties)

    override fun generate(randomizer: Randomizer): Sequence<Sample<V>> =
        source.generate(randomizer).map { MappedSample(it, transform) }

    override fun rebuild(sample: Sample<*>): Sample<V> =
        MappedSample(source.rebuild((sample as MappedSample<*, *>).source), transform)
}

class MappedSample<S, V>(
    val source: Sample<S>,
    private val transform: (S) -> V,
) : Sample<V>() {
    override val value: V = transform(source.value)

    override fun shrink(): List<Sample<V>> =
        source.shrink().map { MappedSample(it, transform) }
}

fun <S, V> Generator<S>.map(transform: (S) -> V): Generator<V> =
    MappedGenerator(this, transform)
