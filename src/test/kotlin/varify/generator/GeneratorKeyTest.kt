package varify.generator

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.generator.collection.*
import varify.generator.primitive.*

class GeneratorKeyTest {

    @Test
    fun `Generators with different properties have different keys`() {
        expectThat(int(0..5).key).isNotEqualTo(int(0..6).key)
        expectThat(from(1, 2).key).isNotEqualTo(from(1, 3).key)
    }

    @Test
    fun `Generators of different types with the same properties have different keys`() {
        expectThat(generatorKey<ValuesGenerator<*>>(0..5)).isNotEqualTo(generatorKey<IntGenerator>(0..5))
    }

    @Test
    fun `Generators with equivalent source generators have equal keys`() {
        expectThat(from(1).orNull().key).isEqualTo(from(1).orNull().key)
        expectThat(list(from(1), int.nonNegative(3)).key)
            .isEqualTo(list(from(1), int.nonNegative(3)).key)
    }

    @Test
    fun `Generators with different source generators have different keys`() {
        expectThat(list(from(1), int.nonNegative(3)).key)
            .isNotEqualTo(list(from(2), int.nonNegative(3)).key)
        expectThat(list(from(1), int.nonNegative(3)).key)
            .isNotEqualTo(set(from(1), int.nonNegative(3)).key)
    }

    @Test
    fun `A key describes its generator`() {
        expectThat(from(1, 2).orNull().key.toString())
            .isEqualTo("NullableGenerator(ValuesGenerator([1, 2]))")
    }
}
