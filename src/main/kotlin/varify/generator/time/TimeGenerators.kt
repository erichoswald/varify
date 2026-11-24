package varify.generator.time

import varify.config.checkConfiguration
import varify.generator.*
import java.math.BigInteger
import java.time.*
import java.time.temporal.*

internal val DEFAULT_MIN_DATE_TIME = LocalDateTime.of(DEFAULT_MIN_YEAR, 1, 1, 0, 0)
internal val DEFAULT_MAX_DATE_TIME = LocalDateTime.of(DEFAULT_MAX_YEAR, 12, 31, 23, 59, 59, 999_999_999)

// Leap days, turns of years, and the overflow of 32-bit seconds since the epoch.
private val SPECIAL_DATE_TIMES = listOf(
    LocalDateTime.of(2000, 2, 29, 12, 0),
    LocalDateTime.of(1999, 12, 31, 23, 59, 59, 999_999_999),
    LocalDateTime.of(2000, 1, 1, 0, 0),
    LocalDateTime.of(2038, 1, 19, 3, 14, 7),
    LocalDateTime.of(2038, 1, 19, 3, 14, 8),
)

private val NANOS_PER_SECOND: BigInteger = BigInteger.valueOf(1_000_000_000)

/**
 * Creates instant generators that shrink towards the epoch, e.g. `instant()` or `instant(precision = MILLIS)`.
 *
 * Instants are generated in steps of a precision, by default whole seconds, so that they look like those of systems
 * that store less than nanoseconds.
 */
@Suppress("ClassName")
object instant {
    /** Generates instants from [min] to [max] (both inclusive), by default in the years 1900 to 2100 (UTC). */
    operator fun invoke(
        min: Instant = DEFAULT_MIN_DATE_TIME.toInstant(ZoneOffset.UTC),
        max: Instant = DEFAULT_MAX_DATE_TIME.toInstant(ZoneOffset.UTC),
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<Instant> =
        precisionStepGenerator(
            min = min,
            max = max,
            precision = precision,
            specialValues = SPECIAL_DATE_TIMES.map { it.toInstant(ZoneOffset.UTC) },
            fromDuration = { Instant.EPOCH + this },
            toDuration = { Duration.between(Instant.EPOCH, this) },
        )

    /** Generates instants in [range]. */
    operator fun invoke(
        range: ClosedRange<Instant>,
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<Instant> =
        invoke(range.start, range.endInclusive, precision)
}

/** Creates date-time generators that shrink towards 1970-01-01T00:00, with a precision like [instant]. */
@Suppress("ClassName")
object localDateTime {
    /** Generates date-times from [min] to [max] (both inclusive), by default in the years 1900 to 2100. */
    operator fun invoke(
        min: LocalDateTime = DEFAULT_MIN_DATE_TIME,
        max: LocalDateTime = DEFAULT_MAX_DATE_TIME,
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<LocalDateTime> =
        precisionStepGenerator(
            min = min,
            max = max,
            precision = precision,
            specialValues = SPECIAL_DATE_TIMES,
            fromDuration = { LocalDateTime.ofInstant(Instant.EPOCH + this, ZoneOffset.UTC) },
            toDuration = { Duration.between(Instant.EPOCH, toInstant(ZoneOffset.UTC)) },
        )

    /** Generates date-times in [range]. */
    operator fun invoke(
        range: ClosedRange<LocalDateTime>,
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<LocalDateTime> =
        invoke(range.start, range.endInclusive, precision)
}

/** Creates time generators that shrink towards midnight, with a precision like [instant]. */
@Suppress("ClassName")
object localTime {
    /** Generates times from [min] to [max] (both inclusive), by default the whole day. */
    operator fun invoke(
        min: LocalTime = LocalTime.MIN,
        max: LocalTime = LocalTime.MAX,
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<LocalTime> =
        precisionStepGenerator(
            min = min,
            max = max,
            precision = precision,
            specialValues = listOf(LocalTime.NOON, LocalTime.MAX),
            fromDuration = { LocalTime.ofNanoOfDay(toNanos()) },
            toDuration = { Duration.ofNanos(toNanoOfDay()) },
        )

    /** Generates times in [range]. */
    operator fun invoke(
        range: ClosedRange<LocalTime>,
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<LocalTime> =
        invoke(range.start, range.endInclusive, precision)
}

/** Creates duration generators that shrink towards [Duration.ZERO], with a precision like [instant]. */
@Suppress("ClassName")
object duration {
    /** Generates durations from [min] to [max] (both inclusive), by default up to a year in both directions. */
    operator fun invoke(
        min: Duration = Duration.ofDays(-365),
        max: Duration = Duration.ofDays(365),
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<Duration> =
        precisionStepGenerator(
            min = min,
            max = max,
            precision = precision,
            specialValues = listOf(precision.duration, precision.duration.negated()),
            fromDuration = { this },
            toDuration = { this },
        )

    /** Generates durations in [range]. */
    operator fun invoke(
        range: ClosedRange<Duration>,
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<Duration> =
        invoke(range.start, range.endInclusive, precision)
}

internal fun <V> precisionStepGenerator(
    min: V,
    max: V,
    precision: TemporalUnit,
    specialValues: List<V>,
    fromDuration: Duration.() -> V,
    toDuration: V.() -> Duration,
): Generator<V> {
    checkConfiguration(!precision.isDurationEstimated || precision == ChronoUnit.DAYS) {
        "precision $precision must have an exact duration"
    }
    val minSteps = min.toDuration().toSteps(precision, roundUp = true)
    val maxSteps = max.toDuration().toSteps(precision)
    return stepGenerator(
        rangeDescription = "$min..$max at precision $precision",
        stepRange = minSteps..maxSteps,
        targetStep = 0,
        specialSteps = specialValues.map { it.toDuration().toSteps(precision) },
        keyProperties = listOf(precision, fromDuration.javaClass),
    ) { step ->
        step.toDuration(precision).fromDuration()
    }
}

private fun Duration.toSteps(precision: TemporalUnit, roundUp: Boolean = false): Long {
    val (quotient, remainder) = quotientAndRemainder(nanos(), precision.duration.nanos())
    val floor = when {
        remainder.signum() < 0 -> quotient - BigInteger.ONE
        else -> quotient
    }
    val steps = when {
        roundUp && remainder.signum() != 0 -> floor + BigInteger.ONE
        else -> floor
    }
    checkConfiguration(steps.bitLength() < Long.SIZE_BITS) {
        "$this has too many steps of $precision to count"
    }
    return steps.toLong()
}

private fun Long.toDuration(precision: TemporalUnit): Duration {
    val (seconds, nanos) = quotientAndRemainder(
        BigInteger.valueOf(this) * precision.duration.nanos(),
        NANOS_PER_SECOND,
    )
    return Duration.ofSeconds(seconds.longValueExact(), nanos.toLong())
}

private fun Duration.nanos(): BigInteger =
    BigInteger.valueOf(seconds) * NANOS_PER_SECOND + BigInteger.valueOf(nano.toLong())

private fun quotientAndRemainder(dividend: BigInteger, divisor: BigInteger): Pair<BigInteger, BigInteger> {
    val quotientAndRemainder = dividend.divideAndRemainder(divisor)
    return Pair(quotientAndRemainder[0], quotientAndRemainder[1])
}
