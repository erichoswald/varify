package varify

import varify.config.VarifyConfiguration
import varify.engine.VariationStage
import varify.event.*
import varify.syntax.VarifyContext

const val SUMMARY_REPORT_KEY = "varify"

// Carries the summary and the original error (if shrunk) of the varification process without stack trace.
class VarifyReport(summary: String, original: Throwable?) : Exception(summary, original, false, false)

/**
 * Runs [test] repeatedly with varying samples until an attempt fails, all variations have been tried, or the attempt limit
 * is reached.
 *
 * The test function requests samples from its [VarifyContext], e.g. `some(generator)` or `oneOf(1, 2, 3)`, and each
 * attempt uses a different combination of samples. Any exception fails an attempt, except for failed assumptions, which
 * abort the varification. After a failed attempt, Varify shrinks its samples to a smaller combination that fails the same
 * way and throws the error of the smallest failing combination.
 *
 * On failure, the thrown error carries a suppressed [VarifyReport] with a summary (including the seed and the original and
 * shrunk samples), and with the original error as its cause if shrinking found a different one. On success, the summary is
 * reported to [reporter] under the key [SUMMARY_REPORT_KEY]. The summary includes the values collected by `collect` and
 * the conditions covered by `cover`. If all attempts pass but a coverage requirement isn't met, the test fails.
 *
 * @param configuration the attempt limits, the randomizer with its seed, and the event listeners.
 * @param reporter receives the success summary and the output of event listeners; discards everything by default.
 * @param testClass identifies failures while shrinking: a smaller combination of samples is only accepted if it fails with
 *   the same exception class on the same line of this class or its nested classes. Defaults to the class of [test].
 * @param test the test function.
 */
fun varify(
    configuration: VarifyConfiguration = VarifyConfiguration(),
    reporter: VarifyReporter = VarifyReporter.None,
    testClass: Class<*>? = null,
    test: VarifyContext.() -> Unit,
) {
    val reportListener = VarifyReportListener()
    val collectedStatistics = CollectedStatistics()
    val reportingConfiguration = configuration
        .withEventListener(reportListener)
        .withEventListener(collectedStatistics)
    fun summary() =
        reportListener.summary() + collectedStatistics.summary()
    var error: Throwable? = null
    try {
        reportingConfiguration.eventListeners.publish(VarificationStarted(configuration, reporter))
        VariationStage(reportingConfiguration, testClass, test)
        collectedStatistics.checkCoverage()
    } catch (expected: Throwable) {
        error = expected
        // Only include the original error if shrinking resulted in a different one (avoiding a circular reference).
        val original = reportListener.originalError?.takeIf { it !== expected }
        expected.addSuppressed(VarifyReport(summary(), original))
        throw expected
    } finally {
        reportingConfiguration.eventListeners.publish(VarificationFinished(error))
    }
    reporter.report(SUMMARY_REPORT_KEY, summary())
}
