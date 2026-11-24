package varify.syntax

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.*
import varify.config.*
import varify.generator.*

class VarifyContextTest {
    private val configuration = VarifyConfiguration(randomizer = TestRandomizer())

    @Test
    fun `The syntax functions are available in a lambda with a VarifyContext receiver`() {
        val samples = mutableListOf<List<String>>()

        varify(configuration) {
            samples += listOf(a(from("a")), an(from("an")), any(from("any")), some(from("some")))
        }

        expectThat(samples).single().isEqualTo(listOf("a", "an", "any", "some"))
    }

    @Test
    fun `The syntax functions are available in a function with a VarifyContext context parameter`() {
        val samples = mutableListOf<List<String>>()

        varify(configuration) {
            samples += sampleAll()
        }

        expectThat(samples).single().isEqualTo(listOf("a", "an", "any", "some"))
    }

    @Test
    fun `oneOf samples each of its values`() {
        val samples = mutableListOf<Int>()

        varify(configuration) {
            samples += oneOf(1, 2, 3)
        }

        expectThat(samples).containsExactlyInAnyOrder(1, 2, 3)
    }

    @Test
    fun `oneOf with an iterable samples its elements, not the iterable itself`() {
        val samples = mutableListOf<Int>()

        varify(configuration) {
            // Specify the type explicitly to force the Iterable overload to be used.
            val sample: Int = oneOf(listOf(1, 2, 3))
            samples += sample
        }

        expectThat(samples).containsExactlyInAnyOrder(1, 2, 3)
    }

    @Test
    fun `oneOf with an int range samples from an int generator instead of listing the range`() {
        val samples = mutableListOf<Int>()

        varify(configuration.copy(variationAttemptsLimit = 3)) {
            samples += oneOf(0..Int.MAX_VALUE)
        }

        expectThat(samples).containsExactlyInAnyOrder(0, 1, Int.MAX_VALUE)
    }

    context(_: VarifyContext)
    private fun sampleAll(): List<String> =
        listOf(a(from("a")), an(from("an")), any(from("any")), some(from("some")))
}
