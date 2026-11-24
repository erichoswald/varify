package varify.generator.primitive

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*

class IntGeneratorTest : GeneratorContract<Int>() {

    override fun subject(): IntGenerator =
        int(0..5)

    @Test
    fun `The generator generates its special values that are in range first`() {
        val generatedValues = subject().allSampleValues()

        expectThat(generatedValues.take(3)).containsExactly(0, 1, 5)
    }

    @Test
    fun `The generator, when permuting a small range, generates the remaining values shuffled`() {
        val generatedValues = subject().allSampleValues()

        // Special values first, then the range reversed by TestRandomizer's shuffle, without duplicates.
        expectThat(generatedValues).containsExactly(0, 1, 5, 4, 3, 2)
    }

    @Test
    fun `The generator generates no more values than the range can provide`() {
        val generatedValues = subject().allSampleValues()

        expectThat(generatedValues).hasSize(6)
    }

    @Test
    fun `The generator with the default (full) range generates special values, then other values`() {
        val generatedValues = int()
            .generate(TestRandomizer())
            .take(10)
            .map { it.value }
            .toList()

        expectThat(generatedValues).hasSize(10)
        expectThat(generatedValues.take(5)).containsExactly(0, 1, -1, Int.MIN_VALUE, Int.MAX_VALUE)
    }

    @Test
    fun `The int function generates all integers by default`() {
        expectThat(int().range).isEqualTo(Int.MIN_VALUE..Int.MAX_VALUE)
    }

    @Test
    fun `The int function takes a minimum, a maximum, or both`() {
        expectThat(int(min = 1).range).isEqualTo(1..Int.MAX_VALUE)
        expectThat(int(max = -1).range).isEqualTo(Int.MIN_VALUE..-1)
        expectThat(int(min = -5, max = 5)).isEqualTo(int(-5..5))
    }

    @Test
    fun `The generator rejects an empty range as a configuration error`() {
        expectThrows<VarifyConfigurationException> { int(min = 1, max = 0) }
            .get { message }.isEqualTo("range 1..0 must not be empty")
    }

    @Test
    fun `The generator generates each value of a large range once`() {
        val subject = int(0..100_000)

        val generatedValues = subject.generate(Randomizer.System())
            .take(1000)
            .map { it.value }
            .toList()

        expectThat(generatedValues).all { isIn(subject.range) }
        expectThat(generatedValues.toSet()).hasSize(1000)
    }

    @Test
    fun `The generator exhausts a range just like a small one, without repeating values`() {
        val values = int(0..2000).allSampleValues(Randomizer.System())

        expectThat(values.sorted()).isEqualTo((0..2000).toList())
    }
}
