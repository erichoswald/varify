package varify.generator.collection

import varify.config.Randomizer
import varify.generator.*

abstract class ListGenerator<C : List<E>, E> : CollectionGenerator<C, E>() {

    override fun selectSample(
        sizeSample: Sample<Int>,
        elementSamples: List<Sample<E>>,
        randomizer: Randomizer,
    ): Sample<C>? {
        val size = sizeSample.value
        if (size == 0) {
            return ListSample(sizeSample, emptyList(), ::collect)
        }

        if (elementSamples.isEmpty()) {
            return null
        }

        // Make sure we have exactly the requested number of elements by either selecting a subset or duplicating existing samples.
        val paddedSamples = elementSamples.selectRandomly(size, randomizer).toMutableList()
        while (paddedSamples.size < size) {
            val duplicate = paddedSamples[randomizer.selectFrom(paddedSamples.indices)]
            val index = randomizer.selectFrom(0..paddedSamples.size)
            paddedSamples.add(index, duplicate)
        }

        return ListSample(sizeSample, paddedSamples, ::collect)
    }

    protected abstract fun collect(elements: List<E>): C
}

/** Creates list generators, e.g. `list(int())` or `list(int(), int.positive(10))`. */
@Suppress("ClassName")
object list {
    /** Generates lists with elements from [elementGen] and sizes from [sizeGen]. */
    operator fun <E> invoke(
        elementGen: Generator<E>,
        sizeGen: Generator<Int> = defaultSizeGen,
    ): ListGenerator<List<E>, E> =
        object : ListGenerator<List<E>, E>() {
            override val sizeGen: Generator<Int> = sizeGen
            override val elementGen: Generator<E> = elementGen
            override fun collect(elements: List<E>): List<E> = elements
        }
}
