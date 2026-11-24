package varify.generator

import varify.config.*
import varify.generator.primitive.*

val boolean: Generator<Boolean> =
    from(false, true)

/** Generates the given values in random order, e.g. `from(1, 2, 3)` or `list(from("a", "b"))`. */
data class ValuesGenerator<V>(private val values: List<V>) : Generator<V> {
    override val key = generatorKey<ValuesGenerator<*>>(values)

    override fun generate(randomizer: Randomizer): Sequence<Sample<V>> =
        randomizer.shuffle(values).asSequence().map(::Unshrinkable)
}

/** Generates [values] in random order. */
fun <V> from(vararg values: V): ValuesGenerator<V> =
    ValuesGenerator(values.toList())

/** Prevents mixing generators by accident. */
@Deprecated("Generates the generators instead of their samples", ReplaceWith("mix(first, *others)"), DeprecationLevel.ERROR)
fun <V> from(first: Generator<V>, vararg others: Generator<V>): Generator<Generator<V>> =
    ValuesGenerator(listOf(first) + others)

/** Generates the elements of [values] in random order. */
fun <V> from(values: Iterable<V>): ValuesGenerator<V> =
    ValuesGenerator(values.toList())

/** Generates the integers in [range] like [int], instead of turning the range into a list like the Iterable overload. */
fun from(range: IntRange): IntGenerator =
    int(range)

/** Generates the chars in [range] like [char], instead of turning the range into a list like the Iterable overload. */
fun from(range: CharRange): CharGenerator =
    char(range)

/** Generates the longs in [range] like [long], instead of turning the range into a list like the Iterable overload. */
fun from(range: LongRange): LongGenerator =
    long(range)

inline fun <reified E : Enum<E>> enum(): Generator<E> =
    from(enumValues<E>().toList())
