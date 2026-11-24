package varify.engine

import varify.generator.*

data class AttributedSample<V>(
    val generator: Generator<V>,
    val sample: Sample<V>,
)

fun Appendable.appendAttributedSamples(attributedSamples: Iterable<AttributedSample<*>>): Appendable =
    append(attributedSamples.joinToString(separator = " ") { it.sample.value.toString() })
