package varify.generator.collection

import varify.config.Randomizer
import varify.generator.*

/**
 * Generates maps with keys from [keyGen], values from [valueGen], and sizes from [sizeGen].
 */
class MapGenerator<K, V>(
    keyGen: Generator<K>,
    valueGen: Generator<V>,
    override val sizeGen: Generator<Int>,
) : SetGenerator<Map<K, V>, Pair<K, V>>() {
    override val elementGen: Generator<Pair<K, V>> = EntryGenerator(keyGen, valueGen)

    override fun collect(elements: Set<Pair<K, V>>): Map<K, V> =
        elements.toMap()
}

/** Creates map generators, e.g. `map(int(), boolean)` or `map(int(), boolean, int.positive(10))`. */
@Suppress("ClassName")
object map {
    /** Generates maps with keys from [keyGen], values from [valueGen], and sizes from [sizeGen]. */
    operator fun <K, V> invoke(
        keyGen: Generator<K>,
        valueGen: Generator<V>,
        sizeGen: Generator<Int> = defaultSizeGen,
    ): MapGenerator<K, V> =
        MapGenerator(keyGen, valueGen, sizeGen)
}

private class EntryGenerator<K, V>(
    private val keyGen: Generator<K>,
    private val valueGen: Generator<V>,
) : Generator<Pair<K, V>> {
    override val key = generatorKey<EntryGenerator<*, *>>(keyGen.key, valueGen.key)

    override fun generate(randomizer: Randomizer): Sequence<Sample<Pair<K, V>>> =
        sequence {
            val values = valueGen.generate(randomizer).iterator()
            val generatedValues = mutableListOf<Sample<V>>()

            // There is no point in reusing keys in the pair sequence because `toMap` only keeps the last entry
            // for each key, but reuse values if necessary.
            for ((index, key) in keyGen.generate(randomizer).withIndex()) {
                if (values.hasNext()) {
                    generatedValues += values.next()
                } else if (generatedValues.isEmpty()) {
                    return@sequence
                }
                yield(EntrySample(key, generatedValues[index % generatedValues.size]))
            }
        }

    override fun rebuild(sample: Sample<*>): Sample<Pair<K, V>> =
        (sample as EntrySample<*, *>).let { entry ->
            EntrySample(keyGen.rebuild(entry.keySample), valueGen.rebuild(entry.valueSample))
        }
}

private class EntrySample<K, V>(
    val keySample: Sample<K>,
    val valueSample: Sample<V>,
) : Sample<Pair<K, V>>() {
    override val value: Pair<K, V> = keySample.value to valueSample.value

    override fun shrink(): List<Sample<Pair<K, V>>> =
        keySample.shrink().map { EntrySample(it, valueSample) } +
            valueSample.shrink().map { EntrySample(keySample, it) }

    // Ensure that the set of entries used for `collect` has distinct keys.
    override fun equals(other: Any?): Boolean =
        other is EntrySample<*, *> && keySample.value == other.keySample.value

    override fun hashCode(): Int =
        keySample.value.hashCode()
}
