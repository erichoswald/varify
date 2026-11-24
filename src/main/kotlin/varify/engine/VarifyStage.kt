package varify.engine

import varify.*
import varify.config.*
import varify.event.*
import varify.syntax.*
import java.lang.reflect.*

interface VarifyStage : VarifyContext {
    val configuration: VarifyConfiguration

    fun publish(event: VarifyEvent) {
        configuration.eventListeners.publish(event)
    }

    override fun collect(label: String, value: Any?) {
        publish(VarifyCollected(label, value))
    }

    override fun cover(label: String, condition: Boolean, minPercent: Int) {
        checkConfiguration(minPercent in 0..100) { "cover(\"$label\", minPercent = $minPercent) must be in 0..100" }
        publish(VarifyCovered(label, condition, minPercent))
    }

    fun attempt(test: VarifyContext.() -> Unit): Throwable? =
        try {
            test()
            null
        } catch (thrown: Throwable) {
            val unwrapped = thrown.unwrapped()
            if (unwrapped.isFatal()) {
                throw unwrapped
            }
            unwrapped
        }
}

// Reflective invocations (e.g. by a test engine) wrap whatever the test function threw.
private fun Throwable.unwrapped(): Throwable =
    (this as? InvocationTargetException)?.cause ?: this

// A stack overflow may be caused by the samples (e.g. deep recursion), so it is treated as a regular failure.
private fun Throwable.isFatal(): Boolean =
    this is VarifyConfigurationException ||
        this is VarifyInternalException ||
        (this is VirtualMachineError && this !is StackOverflowError) ||
        this is InterruptedException
