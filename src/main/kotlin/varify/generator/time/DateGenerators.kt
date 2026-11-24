package varify.generator.time

import varify.generator.*
import varify.generator.primitive.*
import java.time.*

internal const val DEFAULT_MIN_YEAR = 1900
internal const val DEFAULT_MAX_YEAR = 2100

/** Creates date generators that shrink towards 1970-01-01, e.g. `localDate()` or `localDate(min = LocalDate.now())`. */
@Suppress("ClassName")
object localDate {
    /** Generates dates from [min] to [max] (both inclusive), by default in the years 1900 to 2100. */
    operator fun invoke(
        min: LocalDate = LocalDate.of(DEFAULT_MIN_YEAR, 1, 1),
        max: LocalDate = LocalDate.of(DEFAULT_MAX_YEAR, 12, 31),
    ): Generator<LocalDate> =
        stepGenerator(
            rangeDescription = "$min..$max",
            stepRange = min.toEpochDay()..max.toEpochDay(),
            targetStep = 0,
            specialSteps = listOf(
                LocalDate.of(2000, 2, 29),
                LocalDate.of(1900, 2, 28),
                LocalDate.of(1999, 12, 31),
                LocalDate.of(2000, 12, 31),
            ).map(LocalDate::toEpochDay),
            fromStep = LocalDate::ofEpochDay,
        )

    /** Generates dates in [range]. */
    operator fun invoke(range: ClosedRange<LocalDate>): Generator<LocalDate> =
        invoke(range.start, range.endInclusive)
}

/** Creates year generators that shrink towards 1970, e.g. `year()`. */
@Suppress("ClassName")
object year {
    /** Generates years from [min] to [max] (both inclusive), by default 1900 to 2100. */
    operator fun invoke(
        min: Year = Year.of(DEFAULT_MIN_YEAR),
        max: Year = Year.of(DEFAULT_MAX_YEAR),
    ): Generator<Year> =
        stepGenerator(
            rangeDescription = "$min..$max",
            stepRange = min.value.toLong()..max.value.toLong(),
            targetStep = 1970,
            specialSteps = listOf(2000L, 1900L), // Leap year rules of centuries.
        ) { isoYear ->
            Year.of(isoYear.toInt())
        }

    /** Generates years in [range]. */
    operator fun invoke(range: ClosedRange<Year>): Generator<Year> =
        invoke(range.start, range.endInclusive)
}

/** Creates year-month generators that shrink towards 1970-01, e.g. `yearMonth()`. */
@Suppress("ClassName")
object yearMonth {
    /** Generates months from [min] to [max] (both inclusive), by default in the years 1900 to 2100. */
    operator fun invoke(
        min: YearMonth = YearMonth.of(DEFAULT_MIN_YEAR, 1),
        max: YearMonth = YearMonth.of(DEFAULT_MAX_YEAR, 12),
    ): Generator<YearMonth> =
        stepGenerator(
            rangeDescription = "$min..$max",
            stepRange = min.steps()..max.steps(),
            targetStep = YearMonth.of(1970, 1).steps(),
            specialSteps = listOf(
                YearMonth.of(2000, 2),
                YearMonth.of(1900, 2),
                YearMonth.of(1999, 12),
            ).map { it.steps() },
        ) { step ->
            YearMonth.of(Math.floorDiv(step, 12).toInt(), Math.floorMod(step, 12) + 1)
        }

    /** Generates months in [range]. */
    operator fun invoke(range: ClosedRange<YearMonth>): Generator<YearMonth> =
        invoke(range.start, range.endInclusive)

    private fun YearMonth.steps(): Long =
        year * 12L + monthValue - 1
}

/** Creates generators of days in a year that shrink towards January 1, e.g. `monthDay()`. */
@Suppress("ClassName")
object monthDay {
    /** Generates days from [min] to [max] (both inclusive), by default all days including February 29. */
    operator fun invoke(
        min: MonthDay = MonthDay.of(1, 1),
        max: MonthDay = MonthDay.of(12, 31),
    ): Generator<MonthDay> =
        stepGenerator(
            rangeDescription = "$min..$max",
            stepRange = min.steps()..max.steps(),
            targetStep = 1,
            specialSteps = listOf(MonthDay.of(2, 29).steps()),
        ) { dayOfYear ->
            MonthDay.from(LocalDate.ofYearDay(2000, dayOfYear.toInt()))
        }

    /** Generates days in [range]. */
    operator fun invoke(range: ClosedRange<MonthDay>): Generator<MonthDay> =
        invoke(range.start, range.endInclusive)

    private fun MonthDay.steps(): Long =
        atYear(2000).dayOfYear.toLong()
}

/** Creates month generators that shrink towards January, e.g. `month()` or `month(Month.MARCH..Month.MAY)`. */
@Suppress("ClassName")
object month {
    /** Generates months from [min] to [max] (both inclusive), by default all months. */
    operator fun invoke(
        min: Month = Month.JANUARY,
        max: Month = Month.DECEMBER,
    ): Generator<Month> =
        stepGenerator(
            rangeDescription = "$min..$max",
            stepRange = min.value.toLong()..max.value.toLong(),
            targetStep = 1,
            specialSteps = listOf(Month.FEBRUARY.value.toLong()),
        ) { month ->
            Month.of(month.toInt())
        }

    /** Generates months in [range]. */
    operator fun invoke(range: ClosedRange<Month>): Generator<Month> =
        invoke(range.start, range.endInclusive)
}

/** Creates day-of-week generators that shrink towards Monday, e.g. `dayOfWeek()`. */
@Suppress("ClassName")
object dayOfWeek {
    /** Generates days of the week from [min] to [max] (both inclusive), by default all days. */
    operator fun invoke(
        min: DayOfWeek = DayOfWeek.MONDAY,
        max: DayOfWeek = DayOfWeek.SUNDAY,
    ): Generator<DayOfWeek> =
        stepGenerator(
            rangeDescription = "$min..$max",
            stepRange = min.value.toLong()..max.value.toLong(),
            targetStep = 1,
        ) { dayOfWeek ->
            DayOfWeek.of(dayOfWeek.toInt())
        }

    /** Generates days of the week in [range]. */
    operator fun invoke(range: ClosedRange<DayOfWeek>): Generator<DayOfWeek> =
        invoke(range.start, range.endInclusive)
}

/** Creates period generators that shrink towards [Period.ZERO], e.g. `period()` or `period(years = int(0..0))`. */
@Suppress("ClassName")
object period {
    /** Generates periods of the given [years], [months] and [days], by default up to ten years in both directions. */
    operator fun invoke(
        years: Generator<Int> = int(-10..10),
        months: Generator<Int> = int(-11..11),
        days: Generator<Int> = int(-30..30),
    ): Generator<Period> =
        compose(years, months, days) { y, m, d -> Period.of(y, m, d) }
}
