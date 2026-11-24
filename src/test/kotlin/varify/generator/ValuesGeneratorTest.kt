package varify.generator

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.generator.primitive.*

class ValuesGeneratorTest : GeneratorContract<String>() {

    enum class TestEnum { A, B, C }

    override fun subject(): Generator<String> =
        from("a", "b", "c")

    @Test
    fun `The generator generates all its values as samples`() {
        val values = subject().allSampleValues()

        expectThat(values).containsExactlyInAnyOrder("a", "b", "c")
    }

    @Test
    fun `The generator shuffles its values with the randomizer`() {
        expectThat(subject().allSampleValues()).containsExactly("c", "b", "a")
    }

    @Test
    fun `The generator generates the elements of an iterable`() {
        expectThat(from(listOf("a", "b", "c")).allSampleValues()).containsExactlyInAnyOrder("a", "b", "c")
    }

    @Test
    fun `The generator of an int range is an int generator instead of a list of the range`() {
        expectThat(from(0..Int.MAX_VALUE)).isEqualTo(int(0..Int.MAX_VALUE))
    }

    @Test
    fun `The generator of a long range is a long generator instead of a list of the range`() {
        expectThat(from(0..Long.MAX_VALUE)).isEqualTo(long(0..Long.MAX_VALUE))
    }

    @Test
    fun `The generated samples cannot be shrunk`() {
        val samples = subject().allSamples()

        val shrunkSamples = samples.map(Sample<String>::shrink)

        expectThat(shrunkSamples).all { isEmpty() }
    }

    @Test
    fun `The boolean generator generates exactly true and false`() {
        expectThat(boolean.allSampleValues()).containsExactlyInAnyOrder(false, true)
    }

    @Test
    fun `The enum generator generates all enum values`() {
        expectThat(enum<TestEnum>().allSampleValues()).containsExactlyInAnyOrder(TestEnum.A, TestEnum.B, TestEnum.C)
    }
}
