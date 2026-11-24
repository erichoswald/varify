package varify.event

import org.opentest4j.*
import varify.config.*
import kotlin.math.*

private const val VALUES_LIMIT = 10

/**
 * Aggregates the values that the test function collects and the conditions it covers in passing and failing variation
 * attempts, and checks the coverage requirements.
 */
internal class CollectedStatistics : VarifyEventListener {

    private class Coverage(val minPercent: Int) {
        var calls = 0
        var covered = 0
    }

    // In the order of first occurrence.
    private val collected = mutableMapOf<String, MutableMap<Any?, Int>>()
    private val coverages = mutableMapOf<String, Coverage>()

    // The calls of the current attempt, counted only if the attempt isn't rejected.
    private val pending = mutableListOf<VarifyEvent>()
    private var shrinking = false

    override fun onEvent(event: VarifyEvent) {
        when (event) {
            is VariationInitiated -> {
                pending.clear()
            }

            is VarifyCollected, is VarifyCovered -> {
                if (!shrinking) {
                    pending += event
                }
            }

            is VariationPassed -> {
                commit()
            }

            is VariationThrew -> {
                commit()
                shrinking = true
            }

            else -> {
            }
        }
    }

    private fun commit() {
        for (event in pending) {
            when (event) {
                is VarifyCollected -> {
                    collected.getOrPut(event.label) { mutableMapOf() }
                        .merge(event.value, 1, Int::plus)
                }

                is VarifyCovered -> {
                    val coverage = coverages.getOrPut(event.label) { Coverage(event.minPercent) }
                    checkConfiguration(coverage.minPercent == event.minPercent) {
                        "cover(\"${event.label}\") requires both ${coverage.minPercent}% and ${event.minPercent}%"
                    }
                    coverage.calls++
                    if (event.condition) {
                        coverage.covered++
                    }
                }

                else -> {
                }
            }
        }
        pending.clear()
    }

    /** Throws an [AssertionFailedError] if a condition was covered in less than its required percentage of calls. */
    fun checkCoverage() {
        val unmet = coverages.filter { (_, coverage) -> coverage.covered * 100 < coverage.minPercent * coverage.calls }
        if (unmet.isNotEmpty()) {
            throw AssertionFailedError(
                unmet.entries.joinToString("\n") { (label, coverage) ->
                    "Coverage of $label is ${percent(coverage.covered, coverage.calls)} " +
                        "(${coverage.covered} of ${count(coverage.calls, "call")}), required ${coverage.minPercent}%"
                },
            )
        }
    }

    /** The lines for the summary, each starting with a line feed. */
    fun summary(): String =
        buildString {
            for ((label, counts) in collected) {
                val total = counts.values.sum()
                val values = counts.entries.sortedByDescending { it.value }
                append("\n  collected ").append(label).append(": ")
                append(values.take(VALUES_LIMIT).joinToString { (value, count) -> "${render(value)} (${percent(count, total)})" })
                if (values.size > VALUES_LIMIT) {
                    append(", … ").append(values.size - VALUES_LIMIT).append(" more")
                }
            }
            for ((label, coverage) in coverages) {
                append("\n  covered ").append(label).append(": ").append(percent(coverage.covered, coverage.calls))
                append(" (required ").append(coverage.minPercent).append("%)")
            }
        }
}

// Rounded, except that rare values don't show as 0%.
private fun percent(count: Int, total: Int): String {
    val percent = (count * 100.0 / total).roundToInt()
    return if (percent == 0 && count > 0) "<1%" else "$percent%"
}
