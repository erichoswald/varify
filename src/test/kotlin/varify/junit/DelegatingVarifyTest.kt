package varify.junit

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*
import varify.syntax.*

class DelegatingVarifyTest {

    private class FirstSample : VarifyContext {
        override fun <V> invoke(generator: Generator<V>): V =
            generator.generate(TestRandomizer()).first().value

        override fun collect(label: String, value: Any?) {
        }

        override fun cover(label: String, condition: Boolean, minPercent: Int) {
        }
    }

    private val context = DelegatingVarify()

    @Test
    fun `The context returns the samples of its delegate`() {
        context.delegate = FirstSample()

        expectThat(context(from(1))).isEqualTo(1)
    }

    @Test
    fun `The context returns null samples of its delegate`() {
        context.delegate = FirstSample()

        expectThat(context(from(1).orNull())).isNull()
    }

    @Test
    fun `The context cannot be used without a delegate`() {
        expectThrows<VarifyConfigurationException> {
            context(from(1))
        }
    }
}
