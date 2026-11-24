package varify.generator

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.primitive.*

class MixedGeneratorTest : GeneratorContract<Int>() {

    override fun subject(): Generator<Int> =
        mix(from(1, 2, 3), int(10..12))

    @Test
    fun `The generator yields all samples of its alternatives`() {
        expectThat(subject().allSampleValues()).containsExactlyInAnyOrder(1, 2, 3, 10, 11, 12)
    }

    @Test
    fun `The generator yields the samples of each alternative in their order, interleaved`() {
        val values = mix(OrderedGenerator(1, 2, 3), OrderedGenerator(10, 20, 30)).allSampleValues()

        expectThat(values.filter { it < 10 }).containsExactly(1, 2, 3)
        expectThat(values.filter { it >= 10 }).containsExactly(10, 20, 30)
        expectThat(values.take(2)).containsExactlyInAnyOrder(1, 10)
    }

    @Test
    fun `The generator chooses alternatives by their weights`() {
        val values = mix.weighted(int(0..999) to 9, int(1000..1999) to 1)
            .generate(Randomizer.System())
            .take(1000).toList()

        expectThat(values.count { it.value < 1000 }).isGreaterThan(800)
    }

    @Test
    fun `The generator skips alternatives without samples`() {
        val values = mix(from(), from(1, 2)).allSampleValues()

        expectThat(values).containsExactlyInAnyOrder(1, 2)
    }

    @Test
    fun `The generated samples shrink to the first samples of earlier alternatives, then within their own`() {
        val sample = mix(int(0..5), int(100..105))
            .allSamples()
            .first { it.value == 103 }

        expectThat(sample.shrink().map { it.value }).containsExactly(0, 100, 102)
    }

    @Test
    fun `The generator rebuilds a sample with the same alternative`() {
        fun offset(by: Int) = mix(int(0..5), int(100..105).map { it + by })
        val sample = offset(0).allSamples().first { it.value == 103 }

        val rebuilt = offset(1000).rebuild(sample)

        expectThat(rebuilt.value).isEqualTo(1103)
        expectThat(rebuilt.shrink().map { it.value }).containsExactly(0, 1100, 1102)
    }

    @Test
    fun `Generators with different weights have different keys`() {
        expectThat(mix.weighted(int() to 1, long().map { it.toInt() } to 2).key)
            .isNotEqualTo(mix.weighted(int() to 2, long().map { it.toInt() } to 1).key)
    }

    @Test
    fun `The generator rejects missing alternatives and non-positive weights as configuration errors`() {
        expectThrows<VarifyConfigurationException> { mix<Int>() }
        expectThrows<VarifyConfigurationException> { mix.weighted(int() to 0) }
    }
}
