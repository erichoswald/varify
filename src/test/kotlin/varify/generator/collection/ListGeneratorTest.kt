package varify.generator.collection

import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Test
import strikt.api.*
import strikt.assertions.*
import varify.config.VarifyConfigurationException
import varify.generator.*
import varify.generator.primitive.int

class ListGeneratorTest : GeneratorContract<List<Int>>() {
    override fun subject(): Generator<List<Int>> =
        list(int(0..100), int.positive(10))

    @Test
    fun `The generator rebuilds the elements of a sample through its element generator`() {
        fun offset(by: Int) = list(int(0..5).map { it + by }, int.positive(3))
        val sample = offset(0).firstSamples(100).first { it.value.size == 3 }

        expectThat(offset(10).rebuild(sample).value).isEqualTo(sample.value.map { it + 10 })
    }

    @Test
    fun `The generator, without a size generator, generates sizes from 0 to the default maximum size`() {
        val sizes = list(int()).firstSampleValues(1000).map { it.size }

        expectThat(sizes.toSet()).isEqualTo((0..DEFAULT_MAX_SIZE).toSet())
    }

    @Test
    fun `The generator accepts sizes up to the size limit`() {
        val sizes = list(int(), int(SIZE_LIMIT..SIZE_LIMIT)).firstSampleValues(1).map { it.size }

        expectThat(sizes).containsExactly(SIZE_LIMIT)
    }

    @Test
    fun `The generator rejects sizes above the size limit as a configuration error`() {
        expectThrows<VarifyConfigurationException> { list(int(), int.nonNegative()).firstSampleValues(10) }
            .get { message }.isEqualTo("generated size ${Int.MAX_VALUE} exceeds the limit of $SIZE_LIMIT")
    }

    @Test
    fun `The generator generates different lists of the same size`() {
        val values = list(int(), int(3..3)).firstSampleValues(100)

        expectThat(values.toSet()).hasSize(100)
    }

    @Test
    fun `The generator ends when its elements can't make any more different lists`() {
        expectThat(list(int(), int(0..0)).allSampleValues()).isEqualTo(listOf(emptyList()))

        val values = list(from(1, 2), int(0..2)).allSampleValues()
        expectThat(values).isNotEmpty().all { get { size }.isIn(0..2) }
        expectThat(values.toSet()).hasSize(values.size)
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `The generator, when its elements repeat a few values endlessly, ends after the lists of those values`() {
        val lists = list(int().map { it % 2 == 0 }, int(0..2)).allSampleValues()

        expectThat(lists.map { it.toSet() }).containsExactlyInAnyOrder(
            emptySet(), setOf(true), setOf(false), setOf(true, false),
        )
    }
}
