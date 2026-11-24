package varify.generator.collection

import varify.generator.Sample

class ListSample<C, E>(
    override val sizeSample: Sample<Int>,
    override val elementSamples: List<Sample<E>>,
    val collect: (elements: List<E>) -> C,
) : ContainerSample<C, E>() {

    init {
        checkSizeAndElementsSamplesMatch()
    }

    override val value: C by lazy {
        collect(elementSamples.map(Sample<E>::value))
    }

    override fun buildSample(
        size: Sample<Int>,
        elements: List<Sample<E>>,
    ): Sample<C> =
        ListSample(size, elements, collect)
}
