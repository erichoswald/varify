package varify.junit

import varify.config.*
import varify.generator.*
import varify.syntax.*

/**
 * The context passed to a test method. JUnit resolves test method arguments only once, so the same instance is
 * passed to all attempts while it delegates to the stage of the current attempt.
 */
internal class DelegatingVarify : VarifyContext {
    var delegate: VarifyContext? = null

    override fun <V> invoke(generator: Generator<V>): V =
        requireDelegate().invoke(generator)

    override fun collect(label: String, value: Any?) {
        requireDelegate().collect(label, value)
    }

    override fun cover(label: String, condition: Boolean, minPercent: Int) {
        requireDelegate().cover(label, condition, minPercent)
    }

    private fun requireDelegate(): VarifyContext =
        delegate ?: throw VarifyConfigurationException("The Varify context can only be used while its test method runs")
}
