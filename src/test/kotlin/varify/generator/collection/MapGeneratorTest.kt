package varify.generator.collection

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.generator.*
import varify.generator.primitive.*

class MapGeneratorTest : GeneratorContract<Map<Int, String>>() {
    override fun subject(): Generator<Map<Int, String>> =
        map(int(0..100), from("a", "b"), int.nonNegative(5))

    @Test
    fun `The generator generates maps with the sizes of its size generator`() {
        val sizes = subject().firstSampleValues(100).map { it.size }

        expectThat(sizes).contains(0, 1, 5)
    }

    @Test
    fun `The generator cycles through the values when there are fewer values than keys`() {
        val values = subject().firstSampleValues(100).first { it.size == 5 }.values

        expectThat(values.toSet()).containsExactlyInAnyOrder("a", "b")
    }

    @Test
    fun `The generator only generates maps that its finite keys can fill`() {
        val sizes = map(boolean, int(), int(2..5)).firstSampleValues(100).map { it.size }

        expectThat(sizes).containsExactly(2)
    }

    @Test
    fun `The generated samples shrink to fewer entries, then shrink keys and values without duplicating keys`() {
        val sample = map(int(0..100), int(0..100), int.nonNegative(2))
            .firstSamples(100)
            .first { it.value.size == 2 }

        // Shrinking key 1 to 0 would duplicate key 0, so that candidate is dropped.
        expectThat(sample.value).isEqualTo(mapOf(1 to 1, 0 to 0))
        expectThat(sample.shrink().map { it.value })
            .containsExactly(emptyMap(), mapOf(1 to 1), mapOf(0 to 0), mapOf(1 to 0, 0 to 0))
    }

    @Test
    fun `The generator rebuilds the keys and values of a sample`() {
        fun offset(by: Int) = map(
            keyGen = int(range = 0..5).map { it + by },
            valueGen = from(0).map { it + by },
            sizeGen = int.positive(3),
        )
        val sample = offset(0).firstSamples(100).first { it.value.size == 3 }

        val expected = sample.value.keys.associate { it + 10 to 10 }
        expectThat(offset(10).rebuild(sample).value).isEqualTo(expected)
    }

    @Test
    fun `The generator keeps a sample whose rebuilt keys would no longer be distinct`() {
        fun divided(by: Int) = map(int(0..5).map { it / by }, boolean, int.positive(3))
        val sample = divided(1).firstSamples(100).first { it.value.size == 3 }

        expectThat(divided(10).rebuild(sample).value).isEqualTo(sample.value)
    }

    @Test
    fun `The generator, without a size generator, generates sizes up to the default maximum size`() {
        val sizes = map(int(), int()).firstSampleValues(1000).map { it.size }

        expectThat(sizes.max()).isEqualTo(DEFAULT_MAX_SIZE)
    }
}
