package varify.event

import varify.config.*
import varify.engine.*

sealed interface VarifyEvent

data class VarificationStarted(
    val configuration: VarifyConfiguration,
    val reporter: VarifyReporter,
) : VarifyEvent

data class VarificationFinished(
    val error: Throwable?,
) : VarifyEvent

data class VariationInitiated(
    val attempt: Int,
) : VarifyEvent

data class VarifySampled(
    val sampleIndex: Int,
    val attributedSample: AttributedSample<*>,
) : VarifyEvent

data class VarifyCollected(
    val label: String,
    val value: Any?,
) : VarifyEvent

data class VarifyCovered(
    val label: String,
    val condition: Boolean,
    val minPercent: Int,
) : VarifyEvent

data class VariationPassed(
    val attempt: Int,
    val generated: List<AttributedSample<*>>,
) : VarifyEvent

data class VariationRejected(
    val attempt: Int,
    val generated: List<AttributedSample<*>>,
    val reason: Throwable,
) : VarifyEvent

data class VariationThrew(
    val generated: List<AttributedSample<*>>,
    val error: Throwable,
) : VarifyEvent

data class VariationsExhausted(
    val attempts: Int,
) : VarifyEvent

data class VariationsAbandoned(
    val attempts: Int,
) : VarifyEvent

data class VariationsRejectionLimitReached(
    val attempts: Int,
) : VarifyEvent

data class ShrinkStarted(
    val attempt: Int,
    val shrinkIndex: Int,
) : VarifyEvent

data class ShrinkPassed(
    val attempt: Int,
    val shrinkIndex: Int,
) : VarifyEvent

data class ShrinkIgnored(
    val attempt: Int,
    val shrinkIndex: Int,
) : VarifyEvent

data class ShrinkThrew(
    val attempt: Int,
    val shrinkIndex: Int,
    val attributedSamples: List<AttributedSample<*>>,
    val error: Throwable,
) : VarifyEvent

data class ShrinkDiverged(
    val attempt: Int,
    val shrinkIndex: Int,
    val error: Throwable,
) : VarifyEvent

data class ShrinkingExhausted(
    val attempts: Int,
) : VarifyEvent

data class ShrinkingAbandoned(
    val attempts: Int,
) : VarifyEvent
