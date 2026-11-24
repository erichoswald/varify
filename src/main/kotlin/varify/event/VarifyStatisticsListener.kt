package varify.event

import varify.engine.*
import varify.generator.*

const val STATISTICS_REPORT_KEY = "varify.statistics"

private const val MOST_FREQUENT_LIMIT = 3

/**
 * Collects statistics of the variation and shrinking stages and of the samples per position and generator, and reports
 * them under [STATISTICS_REPORT_KEY] when the varification finishes. Only variation attempts count for the samples,
 * because shrink candidates don't represent the generators' distributions.
 */
class VarifyStatisticsListener internal constructor(private val nanoTime: () -> Long) : VarifyEventListener {

    @Suppress("unused")
    constructor() : this(System::nanoTime)

    private var reporter = VarifyReporter.None
    private var seed = 0L
    private var startTime = 0L
    private var shrinkStartTime: Long? = null

    private var variationAttempts = 0
    private var rejectedAttempts = 0
    private var variationOutcome: String? = null
    private var shrinkAttempts = 0
    private var ignoredShrinkAttempts = 0
    private var divergedShrinkAttempts = 0
    private var shrinkOutcome: String? = null

    private val sampleCounts = mutableMapOf<Pair<Int, GeneratorKey>, MutableMap<Any?, Int>>()

    override fun onEvent(event: VarifyEvent) {
        when (event) {
            is VarificationStarted -> startVarification(event)
            is VariationInitiated -> variationAttempts = event.attempt
            is VarifySampled -> sampled(event.sampleIndex, event.attributedSample)
            is VariationPassed, is VarifyCollected, is VarifyCovered -> Unit
            is VariationRejected -> rejectedAttempts++
            is VariationThrew -> variationFailed()
            is VariationsExhausted -> variationOutcome = "all variations tried"
            is VariationsAbandoned -> variationOutcome = "attempt limit reached"
            is VariationsRejectionLimitReached -> variationOutcome = "rejection limit reached"
            is ShrinkStarted -> shrinkAttempts = event.attempt
            is ShrinkPassed, is ShrinkThrew -> Unit
            is ShrinkIgnored -> ignoredShrinkAttempts++
            is ShrinkDiverged -> divergedShrinkAttempts++
            is ShrinkingExhausted -> shrinkOutcome = "exhausted"
            is ShrinkingAbandoned -> shrinkOutcome = "attempt limit reached"
            is VarificationFinished -> finishVarification(event)
        }
    }

    private fun startVarification(event: VarificationStarted) {
        reporter = event.reporter
        seed = event.configuration.randomizer.seed
        startTime = nanoTime()
        shrinkStartTime = null
        variationAttempts = 0
        rejectedAttempts = 0
        variationOutcome = null
        shrinkAttempts = 0
        ignoredShrinkAttempts = 0
        divergedShrinkAttempts = 0
        shrinkOutcome = null
        sampleCounts.clear()
    }

    private fun sampled(sampleIndex: Int, attributedSample: AttributedSample<*>) {
        val isShrinking = shrinkStartTime != null
        if (!isShrinking) {
            val counts = sampleCounts.getOrPut(sampleIndex to attributedSample.generator.key) { mutableMapOf() }
            counts.merge(attributedSample.sample.value, 1, Int::plus)
        }
    }

    private fun variationFailed() {
        variationOutcome = "failed"
        shrinkStartTime = nanoTime()
    }

    private fun finishVarification(event: VarificationFinished) {
        val endTime = nanoTime()
        val aborted = event.error?.let { "aborted (${describe(it)})" }
        val shrinkStartTime = shrinkStartTime
        val log = StringBuilder("Varify statistics: seed ").append(seed)

        log.append("\nvariation: ").append(attempts(variationAttempts))
        if (rejectedAttempts > 0) {
            log.append(" (").append(rejectedAttempts).append(" rejected)")
        }
        log.append(", ").append(variationOutcome ?: aborted)
        log.append(", ").append(millis(startTime, shrinkStartTime ?: endTime))

        if (shrinkStartTime != null) {
            log.append("\nshrinking: ").append(attempts(shrinkAttempts))
            val skipped = listOfNotNull(
                "$ignoredShrinkAttempts ignored".takeIf { ignoredShrinkAttempts > 0 },
                "$divergedShrinkAttempts diverged".takeIf { divergedShrinkAttempts > 0 },
            )
            if (skipped.isNotEmpty()) {
                log.append(skipped.joinToString(prefix = " (", postfix = ")"))
            }
            log.append(", ").append(shrinkOutcome ?: aborted)
            log.append(", ").append(millis(shrinkStartTime, endTime))
        }

        if (sampleCounts.isNotEmpty()) {
            log.append("\nsamples:")
            sampleCounts.entries.sortedBy { it.key.first }.forEach { (position, counts) ->
                log.append("\n  [")
                    .append(position.first)
                    .append("] ")
                    .append(position.second)
                    .append(": ")
                    .append(count(counts.values.sum(), "sample"))
                    .append(", ")
                    .append(counts.size)
                    .append(" distinct")
                val mostFrequent = counts.entries
                    .filter { it.value > 1 }
                    .sortedByDescending { it.value }
                    .take(MOST_FREQUENT_LIMIT)
                if (mostFrequent.isNotEmpty()) {
                    log.append(", most frequent: ")
                        .append(mostFrequent.joinToString { (value, count) -> "${render(value)} ($count)" })
                }
            }
        }

        reporter.report(STATISTICS_REPORT_KEY, log)
    }

    private fun millis(from: Long, to: Long): String =
        "${(to - from) / 1_000_000} ms"
}
