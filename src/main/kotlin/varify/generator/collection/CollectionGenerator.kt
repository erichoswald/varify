package varify.generator.collection

import varify.config.*
import varify.generator.*
import varify.generator.primitive.*

/** The default size of generated collections. */
const val DEFAULT_MAX_SIZE = 100

/** The largest allowed collection size. Guards against unbounded size generators like `int.nonNegative()`. */
const val SIZE_LIMIT = 10_000

/** The size generator of collections without an explicit one: 0 to [DEFAULT_MAX_SIZE]. */
val defaultSizeGen: Generator<Int> = int.nonNegative(DEFAULT_MAX_SIZE)

abstract class CollectionGenerator<C, E> : Generator<C> {
    abstract val sizeGen: Generator<Int>
    abstract val elementGen: Generator<E>

    override val key: GeneratorKey by lazy {
        // Lazy because subclasses initialize sizeGen and elementGen after this class.
        GeneratorKey(this::class, listOf(sizeGen.key, elementGen.key))
    }

    override fun generate(randomizer: Randomizer): Sequence<Sample<C>> =
        sequence {
            val elementList = mutableListOf<Sample<E>>()
            val generatedValues = mutableSetOf<C>()
            val elementIterator = elementGen.generate(randomizer).distinctUntilExhausted().iterator()
            var round = 0
            do {
                // Go through the full sequence of generated sizes for each round, gradually increasing the number of
                // available elements.
                round++
                var exhausted = true
                for (sizeSample in sizeGen.generate(randomizer)) {
                    val size = sizeSample.value
                    checkConfiguration(size >= 0) { "generated size must be non-negative" }
                    checkConfiguration(size <= SIZE_LIMIT) { "generated size $size exceeds the limit of $SIZE_LIMIT" }
                    while (elementList.size < size * round && elementIterator.hasNext()) {
                        elementList += elementIterator.next()
                    }
                    val sample = selectSample(sizeSample, elementList, randomizer)
                    if (sample != null && generatedValues.add(sample.value)) {
                        exhausted = false
                        yield(sample)
                    }
                }
            } while (!exhausted)
        }

    @Suppress("UNCHECKED_CAST")
    override fun rebuild(sample: Sample<*>): Sample<C> =
        (sample as ContainerSample<C, E>).let { container ->
            // We need to keep the size because building the sample will otherwise fail.
            // If a new sample cannot be built, the original sample is returned.
            val rebuiltElementSamples = container.elementSamples.map(elementGen::rebuild)
            container.buildSample(container.sizeSample, rebuiltElementSamples)
                ?: container
        }

    /** Selects a sample of the given size from [elementSamples] (without duplicates), or `null` if they can't fill it. */
    protected abstract fun selectSample(
        sizeSample: Sample<Int>,
        elementSamples: List<Sample<E>>,
        randomizer: Randomizer,
    ): Sample<C>?

    /** Selects up to [count] different elements in random order. */
    protected fun <T> List<T>.selectRandomly(count: Int, randomizer: Randomizer): List<T> =
        randomizer.permutation(0L..<size.toLong()).take(count).map { this[it.toInt()] }.toList()
}
