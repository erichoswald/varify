@file:Suppress("unused")

package varify.syntax

import varify.generator.*

interface VarifyContext {
    operator fun <V> invoke(generator: Generator<V>): V
    fun collect(label: String, value: Any?)
    fun cover(label: String, condition: Boolean, minPercent: Int)
}

context(varify: VarifyContext)
fun <V> a(generator: Generator<V>): V = varify(generator)

context(varify: VarifyContext)
fun <V> an(generator: Generator<V>): V = varify(generator)

context(varify: VarifyContext)
fun <V> any(generator: Generator<V>): V = varify(generator)

context(varify: VarifyContext)
fun <V> some(generator: Generator<V>): V = varify(generator)

context(varify: VarifyContext)
fun <V> oneOf(vararg values: V): V = varify(from(values.toList()))

@Deprecated("Returns a generator instead of a sample", ReplaceWith("some(mix(first, *others))"), DeprecationLevel.ERROR)
context(varify: VarifyContext)
fun <V> oneOf(first: Generator<V>, vararg others: Generator<V>): Generator<V> =
    varify(ValuesGenerator(listOf(first) + others))

context(varify: VarifyContext)
fun <V> oneOf(values: Iterable<V>): V = varify(from(values))

context(varify: VarifyContext)
fun oneOf(range: IntRange): Int = varify(from(range))

context(varify: VarifyContext)
fun oneOf(range: LongRange): Long = varify(from(range))

context(varify: VarifyContext)
fun oneOf(range: CharRange): Char = varify(from(range))

context(varify: VarifyContext)
fun collect(label: String, value: Any?) = varify.collect(label, value)

context(varify: VarifyContext)
fun cover(label: String, condition: Boolean, minPercent: Int) = varify.cover(label, condition, minPercent)
