package varify.generator

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.Randomizer
import varify.config.TestRandomizer
import kotlin.random.*

private const val SAMPLE_COUNT = 1000

abstract class GeneratorContract<V> {

    abstract fun subject(): Generator<V>

    @Test
    fun `The generator has the same key as another generator created with the same values`() {
        expectThat(subject().key).isEqualTo(subject().key)
    }

    @Test
    fun `The generator generates at least one sample`() {
        val subject = subject()

        val result = subject.firstSamples(SAMPLE_COUNT, TestRandomizer())

        expectThat(result).isNotEmpty()
    }

    @Test
    fun `The generator, when invoked with an equivalent random source, creates the same sample values`() {
        val subject = subject()
        val seed = Random.nextLong()
        val firstRunValues = subject.firstSampleValues(SAMPLE_COUNT, Randomizer.System(seed))
        val secondRunValues = subject.firstSampleValues(SAMPLE_COUNT, Randomizer.System(seed))

        expectThat(firstRunValues).containsExactly(secondRunValues)
    }

    @Test
    fun `The generated samples, when shrunk, do not include the original sample values`() {
        val originalSampleInShrunkSamples = subject().firstSamples(SAMPLE_COUNT).map { sample ->
            sample.shrink().filter { it.value == sample.value }
        }

        expectThat(originalSampleInShrunkSamples).all { isEmpty() }
    }
}
