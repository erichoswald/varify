package varify.generator.collection

import org.junit.jupiter.api.Test
import strikt.api.*
import strikt.assertions.*
import varify.VarifyInternalException
import varify.generator.Unshrinkable
import varify.generator.primitive.*

class SetSampleTest {
    @Test
    fun `The sample reduces the number of elements`() {
        val subject = SetSample(IntSample(1) { listOf(0) }, setOf(Unshrinkable(0))) { it }

        val result = subject.shrink()

        expectThat(result)
            .hasSize(1)
            .get(0).isA<SetSample<*, *>>()
            .get(SetSample<*, *>::elementSamples).isEmpty()
    }

    @Test
    fun `The sample shrinks individual elements`() {
        val subject = SetSample(Unshrinkable(1), setOf(IntSample(1) { listOf(0) })) { it }

        val result = subject.shrink()

        expectThat(result)
            .hasSize(1)
            .get(0).isA<SetSample<*, *>>()
            .get(SetSample<*, *>::elementSamples)
            .hasSize(1)
            .get(Set<*>::single).isA<IntSample>()
            .get(IntSample::value).isEqualTo(0)
    }

    @Test
    fun `The sample tries each shrunk element separately`() {
        val subject = SetSample(Unshrinkable(1), setOf(IntSample(4, BisectingIntShrinker(0)))) { it }

        val result = subject.shrink()

        expectThat(result.map { it.value }).containsExactly(setOf(0), setOf(2), setOf(3))
    }

    @Test
    fun `The sample rejects a number of elements that does not match its size as an internal error`() {
        expectThrows<VarifyInternalException> {
            SetSample(Unshrinkable(2), setOf(Unshrinkable(0))) { it }
        }.get { message }.isEqualTo(
            "Number of elements (1) does not match size (2) (this is a bug in Varify, please report it)",
        )
    }
}
