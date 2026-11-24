package varify.generator.collection

import varify.config.Randomizer
import varify.generator.*

abstract class SetGenerator<C, E> : CollectionGenerator<C, E>() {

    override fun selectSample(
        sizeSample: Sample<Int>,
        elementSamples: List<Sample<E>>,
        randomizer: Randomizer,
    ): Sample<C>? =
        sizeSample.value
            .takeIf { it <= elementSamples.size }
            ?.let { size ->
                val selection = elementSamples.selectRandomly(size, randomizer).toSet()
                SetSample(sizeSample, selection, ::collect)
            }

    protected abstract fun collect(elements: Set<E>): C
}

/** Creates set generators, e.g. `set(int())` or `set(int(), int.positive(10))`. */
@Suppress("ClassName")
object set {
    /** Generates sets with elements from [elementGen] and sizes from [sizeGen]. Sizes that the elements can't fill are skipped. */
    operator fun <E> invoke(
        elementGen: Generator<E>,
        sizeGen: Generator<Int> = defaultSizeGen,
    ): SetGenerator<Set<E>, E> =
        object : SetGenerator<Set<E>, E>() {
            override val sizeGen: Generator<Int> = sizeGen
            override val elementGen: Generator<E> = elementGen
            override fun collect(elements: Set<E>): Set<E> = elements
        }
}
