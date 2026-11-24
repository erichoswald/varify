package varify.event

import varify.config.VarifyConfiguration
import varify.engine.AttributedSample
import varify.engine.appendAttributedSamples

private const val LF = "\n"

class VarifyReportListener : VarifyEventListener {

    private var configuration: VarifyConfiguration? = null
    private var variationAttempts = 0
    private var rejectedAttempts = 0
    private var variationsOutcome: String? = null
    private var shrinkAttempts = 0
    private var shrinkAttemptsIgnored = 0
    private var shrinkAttemptsDiverged = 0
    private var shrunkAttempt = 0
    var originalError: Throwable? = null
        private set
    private var unshrunk: List<AttributedSample<*>> = emptyList()
    private var shrunk: List<AttributedSample<*>> = emptyList()

    fun hasError(): Boolean =
        originalError != null

    fun summary(): String {
        val sb = StringBuilder("Varify: ")
        if (hasError()) {
            sb.append("failed on attempt ").append(variationAttempts)
            appendRejected(sb)
            if (shrinkAttempts == 0) {
                sb.append(", unshrinkable")
            } else if (shrunk.isEmpty()) {
                sb.append(", unable to shrink in ").append(shrinkAttempts).append(" attempts")
            } else {
                sb.append(", shrunk in attempt ").append(shrunkAttempt).append(" of ").append(shrinkAttempts)
            }
            val skipped = listOfNotNull(
                "ignored $shrinkAttemptsIgnored".takeIf { shrinkAttemptsIgnored > 0 },
                "diverged $shrinkAttemptsDiverged".takeIf { shrinkAttemptsDiverged > 0 },
            )
            if (skipped.isNotEmpty()) {
                sb.append(skipped.joinToString(prefix = " (", postfix = ")"))
            }
            sb.append(LF).append("  seed: ").append(configuration?.randomizer?.seed)
            sb.append(LF).append("  generated: ").appendAttributedSamples(unshrunk)
            if (shrinkAttempts > 0) {
                sb.append(LF).append("  shrunk: ").appendAttributedSamples(shrunk)
            }
        } else if (variationsOutcome != null) {
            val passedAttempts = variationAttempts - rejectedAttempts
            if (passedAttempts == 0) {
                sb.append("rejected all ").append(variationAttempts).append(attemptsNoun(variationAttempts))
            } else {
                sb.append("passed ").append(passedAttempts).append(attemptsNoun(passedAttempts))
                appendRejected(sb)
            }
            sb.append(", ").append(variationsOutcome)
            sb.append(LF).append("  seed: ").append(configuration?.randomizer?.seed)
        } else {
            sb.append("aborted on attempt ").append(variationAttempts)
            sb.append(LF).append("  seed: ").append(configuration?.randomizer?.seed)
        }
        return sb.toString()
    }

    private fun appendRejected(sb: StringBuilder) {
        if (rejectedAttempts > 0) {
            sb.append(" (").append(rejectedAttempts).append(" rejected)")
        }
    }

    private fun attemptsNoun(count: Int): String =
        if (count == 1) " attempt" else " attempts"

    override fun onEvent(event: VarifyEvent) {
        when (event) {
            is VarificationStarted -> {
                configuration = event.configuration
            }

            is VariationInitiated -> {
                variationAttempts = event.attempt
            }

            is VariationsExhausted -> {
                variationsOutcome = "all variations tried"
            }

            is VariationsAbandoned -> {
                variationsOutcome = "attempt limit reached"
            }

            is VariationsRejectionLimitReached -> {
                variationsOutcome = "rejection limit reached"
            }

            is VariationRejected -> {
                rejectedAttempts++
            }

            is VariationThrew -> {
                originalError = event.error
                unshrunk = event.generated
            }

            is ShrinkStarted -> {
                shrinkAttempts = event.attempt
            }

            is ShrinkIgnored -> {
                shrinkAttemptsIgnored++
            }

            is ShrinkDiverged -> {
                shrinkAttemptsDiverged++
            }

            is ShrinkThrew -> {
                shrunkAttempt = event.attempt
                shrunk = event.attributedSamples
            }

            else -> {}
        }
    }
}
