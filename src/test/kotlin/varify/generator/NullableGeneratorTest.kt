package varify.generator

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isNull

class NullableGeneratorTest : GeneratorContract<String?>() {

    override fun subject(): Generator<String?> =
        NullableGenerator(from("a", "b"))

    @Test
    fun `The generator generates a null value in addition to all source values`() {
        val values = subject().allSampleValues()

        expectThat(values.first()).isNull()
        expectThat(values.drop(1)).containsExactlyInAnyOrder("a", "b")
    }

    @Test
    fun `The generator rebuilds null as null and other samples through its source`() {
        fun prefixed(prefix: String) = NullableGenerator(OrderedGenerator("a", "b").map { prefix + it })
        val samples = prefixed("x").allSamples()

        expectThat(samples.map { prefixed("y").rebuild(it).value }).containsExactly(null, "ya", "yb")
    }
}
