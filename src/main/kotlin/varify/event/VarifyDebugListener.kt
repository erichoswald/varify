package varify.event

import varify.engine.*

const val DEBUG_REPORT_KEY = "varify.debug"

/**
 * Logs each attempt with its outcome and samples, and reports the log under [DEBUG_REPORT_KEY] when the varification
 * finishes. During shrinking, `*` marks the sample whose shrink candidate the attempt tries.
 */
class VarifyDebugListener : VarifyEventListener {

    private var reporter = VarifyReporter.None
    private val log = StringBuilder()

    // The attempt in progress, if any.
    private var attempt: String? = null
    private var focusIndex: Int? = null
    private val samples = mutableListOf<AttributedSample<*>>()

    override fun onEvent(event: VarifyEvent) {
        when (event) {
            is VarificationStarted -> startVarification(event)
            is VariationInitiated -> startAttempt("variation ${event.attempt}", focusIndex = null)
            is ShrinkStarted -> startAttempt("shrink ${event.attempt}", event.shrinkIndex)
            is VarifySampled -> samples += event.attributedSample
            is VarifyCollected, is VarifyCovered -> Unit
            is VariationPassed -> finishAttempt("passed")
            is VariationRejected -> finishAttempt("rejected (${describe(event.reason)})")
            is VariationThrew -> finishAttempt("failed (${describe(event.error)})")
            is ShrinkPassed -> finishAttempt("passed")
            is ShrinkIgnored -> finishAttempt("ignored")
            is ShrinkDiverged -> finishAttempt("diverged (${describe(event.error)})")
            is ShrinkThrew -> finishAttempt("failed the same way (${describe(event.error)})")
            is VariationsExhausted -> line("all variations tried after ${attempts(event.attempts)}")
            is VariationsAbandoned -> line("attempt limit reached after ${attempts(event.attempts)}")
            is VariationsRejectionLimitReached -> line("rejection limit reached after ${attempts(event.attempts)}")
            is ShrinkingExhausted -> line("shrinking exhausted after ${attempts(event.attempts)}")
            is ShrinkingAbandoned -> line("shrink attempt limit reached after ${attempts(event.attempts)}")
            is VarificationFinished -> finishVarification(event)
        }
    }

    private fun startVarification(event: VarificationStarted) {
        reporter = event.reporter
        log.clear()
        attempt = null
        log.append("Varify debug: seed ").append(event.configuration.randomizer.seed)
    }

    private fun startAttempt(attempt: String, focusIndex: Int?) {
        this.attempt = attempt
        this.focusIndex = focusIndex
        samples.clear()
    }

    private fun finishAttempt(outcome: String) {
        val attempt = attempt ?: return
        line("$attempt: $outcome")
        samples.forEachIndexed { index, (generator, sample) ->
            val indent = if (index == focusIndex) " *" else "  "
            log.append('\n')
                .append(indent)
                .append('[')
                .append(index)
                .append("] ")
                .append(generator.key)
                .append(" = ")
                .append(render(sample.value))
        }
        this.attempt = null
    }

    private fun finishVarification(event: VarificationFinished) {
        // An attempt is still in progress if Varify itself failed, e.g. on a configuration error.
        event.error?.let { finishAttempt("aborted (${describe(it)})") }
        reporter.report(DEBUG_REPORT_KEY, log)
    }

    private fun line(text: String) {
        log.append('\n').append(text)
    }
}
