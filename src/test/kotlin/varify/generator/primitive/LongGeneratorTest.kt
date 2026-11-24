package varify.generator.primitive

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*

class LongGeneratorTest : GeneratorContract<Long>() {

    override fun subject(): LongGenerator =
        long(0L..5L)

    @Test
    fun `The generator generates its special values that are in range first, then the others shuffled`() {
        // TestRandomizer reverses when permuting.
        expectThat(subject().allSampleValues()).containsExactly(0, 1, 5, 4, 3, 2)
    }

    @Test
    fun `The generator with the full range generates special values, then values from all over the range`() {
        val values = long().generate(Randomizer.System()).take(1000).map { it.value }.toList()

        expectThat(values.take(5)).containsExactly(0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE)
        expectThat(values.toSet()).hasSize(1000)
        expectThat(values.count { it < Int.MIN_VALUE || it > Int.MAX_VALUE }).isGreaterThan(900)
    }

    @Test
    fun `The long function takes a minimum, a maximum, or both`() {
        expectThat(long().range).isEqualTo(Long.MIN_VALUE..Long.MAX_VALUE)
        expectThat(long(min = 1).range).isEqualTo(1..Long.MAX_VALUE)
        expectThat(long(max = -1).range).isEqualTo(Long.MIN_VALUE..-1)
        expectThat(long(min = -5, max = 5)).isEqualTo(long(-5L..5L))
    }

    @Test
    fun `The long object provides non-negative and positive longs`() {
        expectThat(long.nonNegative(10).range).isEqualTo(0L..10L)
        expectThat(long.positive().range).isEqualTo(1..Long.MAX_VALUE)
    }

    @Test
    fun `The generator rejects an empty range as a configuration error`() {
        expectThrows<VarifyConfigurationException> { long(min = 1, max = 0) }
            .get { message }.isEqualTo("range 1..0 must not be empty")
    }

    @Test
    fun `The generated samples shrink towards zero`() {
        val sample = long().generate(TestRandomizer()).first { it.value == Long.MIN_VALUE }

        expectThat(sample.shrink().map { it.value }.first()).isEqualTo(0)
    }
}
