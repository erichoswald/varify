package varify.generator

import varify.config.*

data class OrderedGenerator<V>(val values: List<V>) : Generator<V> {
    constructor(vararg values: V) : this(values.toList())

    override val key = generatorKey<OrderedGenerator<*>>(values)

    override fun generate(randomizer: Randomizer): Sequence<Sample<V>> =
        values.asSequence().map(::Unshrinkable)
}

fun <V> Generator<V>.firstSampleValues(
    count: Int,
    randomizer: Randomizer = TestRandomizer(),
): List<V> =
    firstSamples(count, randomizer).map(Sample<V>::value)

fun <V> Generator<V>.firstSamples(
    count: Int,
    randomizer: Randomizer = TestRandomizer(),
): List<Sample<V>> =
    generate(randomizer).take(count).toList()

fun <V> Generator<V>.allSampleValues(
    randomizer: Randomizer = TestRandomizer(),
): List<V> =
    allSamples(randomizer).map(Sample<V>::value)

fun <V> Generator<V>.allSamples(
    randomizer: Randomizer = TestRandomizer(),
): List<Sample<V>> =
    generate(randomizer).toList()
