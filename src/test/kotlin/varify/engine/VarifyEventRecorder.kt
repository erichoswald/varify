package varify.engine

import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.event.*
import varify.generator.*

typealias VarifyEventAssertion = Assertion.Builder<VarifyEvent>.() -> Unit

class VarifyEventRecorder : VarifyEventListener {
    val events = mutableListOf<VarifyEvent>()

    fun configuration(
        variationAttemptsLimit: Int = 10,
        shrinkAttemptsLimit: Int = 100,
        randomizer: Randomizer = TestRandomizer(),
    ): VarifyConfiguration =
        VarifyConfiguration(
            variationAttemptsLimit = variationAttemptsLimit,
            shrinkAttemptsLimit = shrinkAttemptsLimit,
            randomizer = randomizer,
            eventListeners = listOf(this),
        )

    override fun onEvent(event: VarifyEvent) {
        events += event
    }

    operator fun get(index: Int): VarifyEvent =
        events[index]
}

fun Assertion.Builder<VarifyEventRecorder>.hasEvent(assertion: VarifyEventAssertion) {
    get(VarifyEventRecorder::events).any(assertion)
}

fun Assertion.Builder<VarifyEventRecorder>.hasExactlyEvents(vararg assertions: VarifyEventAssertion) {
    val events = get(VarifyEventRecorder::events)
    assertions.forEachIndexed { i, assertion ->
        events.elementAt(i).assertion()
    }
    events.hasSize(assertions.size)
}

fun isVarifySampled(
    sampleIndex: Int,
    value: Any?,
    generator: Generator<*>? = null,
): VarifyEventAssertion =
    {
        isA<VarifySampled>().and {
            get(VarifySampled::sampleIndex).isEqualTo(sampleIndex)
            get { attributedSample.sample.value }.isEqualTo(value)
            if (generator != null) {
                get { attributedSample.generator }.isEqualTo(generator)
            }
        }
    }

fun isShrinkStarted(
    attempt: Int,
    shrinkIndex: Int,
): VarifyEventAssertion =
    {
        isEqualTo(ShrinkStarted(attempt, shrinkIndex))
    }

fun isShrinkThrew(attempt: Int, shrinkIndex: Int): VarifyEventAssertion =
    {
        isA<ShrinkThrew>().and {
            get(ShrinkThrew::attempt).isEqualTo(attempt)
            get(ShrinkThrew::shrinkIndex).isEqualTo(shrinkIndex)
        }
    }

fun isShrinkPassed(attempt: Int, shrinkIndex: Int): VarifyEventAssertion =
    {
        isA<ShrinkPassed>().and {
            get(ShrinkPassed::attempt).isEqualTo(attempt)
            get(ShrinkPassed::shrinkIndex).isEqualTo(shrinkIndex)
        }
    }

fun isShrinkIgnored(attempt: Int, shrinkIndex: Int): VarifyEventAssertion =
    {
        isA<ShrinkIgnored>().and {
            get(ShrinkIgnored::attempt).isEqualTo(attempt)
            get(ShrinkIgnored::shrinkIndex).isEqualTo(shrinkIndex)
        }
    }

fun isShrinkDiverged(attempt: Int, shrinkIndex: Int): VarifyEventAssertion =
    {
        isA<ShrinkDiverged>().and {
            get(ShrinkDiverged::attempt).isEqualTo(attempt)
            get(ShrinkDiverged::shrinkIndex).isEqualTo(shrinkIndex)
        }
    }

fun isShrinkingAttemptLimitReached(attempts: Int): VarifyEventAssertion =
    {
        isEqualTo(ShrinkingAbandoned(attempts))
    }

fun isShrinkingStageExhausted(attempts: Int): VarifyEventAssertion =
    {
        isEqualTo(ShrinkingExhausted(attempts))
    }
