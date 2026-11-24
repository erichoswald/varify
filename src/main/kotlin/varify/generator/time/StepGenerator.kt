package varify.generator.time

import varify.config.*
import varify.generator.*
import varify.generator.primitive.*

/**
 * Generates the values of a type that counts steps from a reference, e.g. days since the epoch, by generating the steps
 * in [stepRange]. The target and the special values come first, and the samples shrink towards the target.
 */
internal fun <V> stepGenerator(
    rangeDescription: String,
    stepRange: LongRange,
    targetStep: Long,
    specialSteps: List<Long> = emptyList(),
    keyProperties: List<Any> = emptyList(),
    fromStep: (Long) -> V,
): Generator<V> {
    checkConfiguration(!stepRange.isEmpty()) {
        "range $rangeDescription must not be empty"
    }
    val target = targetStep.coerceIn(stepRange)
    val specials = listOf(target, stepRange.first, stepRange.last) + specialSteps
    return MappedGenerator(LongGenerator(stepRange, specials, BisectingLongShrinker(target)), keyProperties, fromStep)
}
