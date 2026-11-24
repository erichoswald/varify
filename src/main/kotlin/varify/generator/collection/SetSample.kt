package varify.generator.collection

import varify.generator.Sample

class SetSample<C, E>(
    override val sizeSample: Sample<Int>,
    override val elementSamples: Set<Sample<E>>,
    val collect: (elements: Set<E>) -> C,
) : ContainerSample<C, E>() {

    init {
        checkSizeAndElementsSamplesMatch()
    }

    override val value: C by lazy {
        collect(elementSamples.map(Sample<E>::value).toSet())
    }

    override fun buildSample(
        size: Sample<Int>,
        elements: List<Sample<E>>,
    ): Sample<C>? =
        // Some elements may have been shrunk to the same value, so make sure we have enough distinct elements.
        elements
            .toSet()
            .takeIf { it.size == size.value }
            ?.let { SetSample(size, it, collect) }
}
