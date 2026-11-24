package varify.generator.collection

import varify.checkInternal
import varify.generator.Sample

abstract class ContainerSample<C, E> : Sample<C>() {

    abstract val sizeSample: Sample<Int>
    abstract val elementSamples: Collection<Sample<E>>

    protected fun checkSizeAndElementsSamplesMatch() {
        // Subclasses should explicitly call this in an init block.
        // Initialization order prevents an init block right here because properties aren't initialized yet.
        checkInternal(elementSamples.size == sizeSample.value) {
            "Number of elements (${elementSamples.size}) does not match size (${sizeSample.value})"
        }
    }

    override fun shrink(): List<Sample<C>> {
        val candidates = mutableListOf<Sample<C>>()
        addCandidatesWithFewerElements(candidates)
        addCandidatesWithShrunkElements(candidates)
        return candidates
    }

    private fun addCandidatesWithFewerElements(candidates: MutableList<Sample<C>>) {
        for (shrunkSizeSample in sizeSample.shrink()) {
            if (shrunkSizeSample.value == 0) {
                addCandidate(shrunkSizeSample, emptyList(), candidates)
            } else {
                for (chunk in elementSamples.windowed(shrunkSizeSample.value)) {
                    addCandidate(shrunkSizeSample, chunk, candidates)
                }
            }
        }
    }

    private fun addCandidatesWithShrunkElements(candidates: MutableList<Sample<C>>) {
        val elementSamples = elementSamples.toList()
        val shrunkElementSamples = elementSamples.map(Sample<E>::shrink)
        shrunkElementSamples.forEachIndexed { index, samples ->
            for (shrunk in samples) {
                val elements = elementSamples.toMutableList().apply { set(index, shrunk) }
                addCandidate(sizeSample, elements, candidates)
            }
        }
    }

    private fun addCandidate(size: Sample<Int>, elements: List<Sample<E>>, candidates: MutableList<Sample<C>>) {
        buildSample(size, elements)?.let(candidates::add)
    }

    internal abstract fun buildSample(size: Sample<Int>, elements: List<Sample<E>>): Sample<C>?
}
