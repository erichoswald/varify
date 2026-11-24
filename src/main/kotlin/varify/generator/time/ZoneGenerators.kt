package varify.generator.time

import varify.generator.*
import varify.generator.primitive.*
import java.time.*
import java.time.temporal.*

/**
 * Creates zone offset generators that shrink towards UTC, e.g. `zoneOffset()`. Offsets are generated in steps of 15
 * minutes, like those in use today.
 */
@Suppress("ClassName")
object zoneOffset {
    private const val OFFSET_STEP_SECONDS = 15 * 60

    private val specialSteps = listOf("+05:30", "+05:45", "-03:30", "+14:00", "-12:00")
        .map { (ZoneOffset.of(it).totalSeconds / OFFSET_STEP_SECONDS).toLong() }

    private fun stepRange(min: ZoneOffset, max: ZoneOffset): LongRange {
        val minSteps = Math.ceilDiv(min.totalSeconds, OFFSET_STEP_SECONDS).toLong()
        val maxSteps = Math.floorDiv(max.totalSeconds, OFFSET_STEP_SECONDS).toLong()
        return minSteps..maxSteps
    }

    /**
     * Generates offsets from [min] to [max] (both inclusive), by default from -18:00 to +18:00.
     *
     * There is no equivalent range-based option because [ZoneOffset] compares based on absolute values.
     */
    operator fun invoke(
        min: ZoneOffset = ZoneOffset.MIN,
        max: ZoneOffset = ZoneOffset.MAX,
    ): Generator<ZoneOffset> =
        stepGenerator(
            rangeDescription = "$min..$max",
            stepRange = stepRange(min, max),
            targetStep = 0,
            specialSteps = specialSteps,
        ) { step ->
            ZoneOffset.ofTotalSeconds(step.toInt() * OFFSET_STEP_SECONDS)
        }
}

/**
 * Creates zone ID generators that shrink towards UTC, e.g. `zoneId()`.
 *
 * The zones come from the time zone database of the JVM, so other Java versions may generate other zones for a seed.
 */
@Suppress("ClassName")
object zoneId {
    // Zones with daylight saving time in both hemispheres, and with offsets that are not whole hours.
    private val specialZoneIds =
        listOf("UTC", "Europe/Zurich", "America/New_York", "Australia/Sydney", "Asia/Kolkata", "Asia/Kathmandu")

    private val zoneIds: List<String> by lazy {
        specialZoneIds + (ZoneId.getAvailableZoneIds() - specialZoneIds.toSet()).sorted()
    }
    private val indexGenerator by lazy {
        IntGenerator(zoneIds.indices, specialZoneIds.indices.toList(), BisectingIntShrinker(0))
    }

    /** Generates the region-based zones of the JVM, the zones with daylight saving time or unusual offsets first. */
    operator fun invoke(): Generator<ZoneId> =
        MappedGenerator(indexGenerator) { step ->
            ZoneId.of(zoneIds[step])
        }
}

/** Creates generators of date-times with an offset, e.g. `offsetDateTime()` or `offsetDateTime(offset = from(UTC))`. */
@Suppress("ClassName")
object offsetDateTime {
    /**
     * Generates date-times whose local date-time is from [min] to [max] (both inclusive), by default in the years 1900
     * to 2100, with an offset of [offset], and with a precision like [instant].
     */
    operator fun invoke(
        min: LocalDateTime = DEFAULT_MIN_DATE_TIME,
        max: LocalDateTime = DEFAULT_MAX_DATE_TIME,
        offset: Generator<ZoneOffset> = zoneOffset(),
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<OffsetDateTime> =
        compose(localDateTime(min, max, precision), offset) { dateTime, zoneOffset ->
            OffsetDateTime.of(dateTime, zoneOffset)
        }

    /** Generates date-times whose local date-time is in [range], like the other overload. */
    operator fun invoke(
        range: ClosedRange<LocalDateTime>,
        offset: Generator<ZoneOffset> = zoneOffset(),
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<OffsetDateTime> =
        invoke(range.start, range.endInclusive, offset, precision)
}

/**
 * Creates generators of date-times in a zone, e.g. `zonedDateTime()` or `zonedDateTime(zone = from(ZoneId.of("CET")))`.
 *
 * About a quarter of the date-times move close to a transition of their zone (e.g. when clocks change for daylight
 * saving time), within two hours of it: before and after a gap, and with both offsets of an overlap. Such date-times
 * shrink towards the transition and to the plain date-time.
 */
@Suppress("ClassName")
object zonedDateTime {
    /**
     * Generates date-times whose local date-time is from [min] to [max] (both inclusive), by default in the years 1900
     * to 2100, in a zone of [zone], with a precision like [instant]. A local date-time in a gap of the zone (when
     * clocks are set forward) moves forward by the length of the gap, see [ZonedDateTime.of], so it can exceed [max].
     */
    operator fun invoke(
        min: LocalDateTime = DEFAULT_MIN_DATE_TIME,
        max: LocalDateTime = DEFAULT_MAX_DATE_TIME,
        zone: Generator<ZoneId> = zoneId(),
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<ZonedDateTime> {
        val transitionOffsets = mix.weighted(
            from<Duration?>(null) to 3,
            duration(Duration.ofHours(-2), Duration.ofHours(2), precision) to 1,
        )
        return compose(localDateTime(min, max, precision), zone, transitionOffsets) { dateTime, zoneId, offset ->
            offset
                ?.let { nearTransition(dateTime, zoneId, offset, min..max) }
                ?: ZonedDateTime.of(dateTime, zoneId)
        }
    }

    /** Generates date-times whose local date-time is in [range], like the other overload. */
    operator fun invoke(
        range: ClosedRange<LocalDateTime>,
        zone: Generator<ZoneId> = zoneId(),
        precision: TemporalUnit = ChronoUnit.SECONDS,
    ): Generator<ZonedDateTime> =
        invoke(range.start, range.endInclusive, zone, precision)

    private fun nearTransition(
        dateTime: LocalDateTime,
        zone: ZoneId,
        offset: Duration,
        range: ClosedRange<LocalDateTime>,
    ): ZonedDateTime? {
        val instant = dateTime.atZone(zone).toInstant()
        return listOfNotNull(zone.rules.nextTransition(instant), zone.rules.previousTransition(instant))
            .map { ZonedDateTime.ofInstant(it.instant + offset, zone) }
            .firstOrNull { it.toLocalDateTime() in range }
    }
}
