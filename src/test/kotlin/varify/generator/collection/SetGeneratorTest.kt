package varify.generator.collection

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.generator.*
import varify.generator.primitive.*

class SetGeneratorTest : GeneratorContract<Set<Int>>() {
    override fun subject(): Generator<Set<Int>> =
        set(int(0..100), int.positive(10))

    @Test
    fun `The generator generates all sets of a few elements, then ends`() {
        val values = set(boolean, int(0..2)).allSampleValues()

        expectThat(values).containsExactlyInAnyOrder(emptySet(), setOf(false), setOf(true), setOf(false, true))
    }
}
