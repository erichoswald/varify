package varify.generator.time

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*
import varify.generator.primitive.*
import java.time.*

class DateGeneratorsTest {

    @Nested
    inner class LocalDates : GeneratorContract<LocalDate>() {
        override fun subject(): Generator<LocalDate> =
            localDate()

        @Test
        fun `The generator generates the epoch, the bounds, and special dates first`() {
            expectThat(localDate().firstSampleValues(7)).containsExactly(
                LocalDate.of(1970, 1, 1),
                LocalDate.of(1900, 1, 1),
                LocalDate.of(2100, 12, 31),
                LocalDate.of(2000, 2, 29),
                LocalDate.of(1900, 2, 28),
                LocalDate.of(1999, 12, 31),
                LocalDate.of(2000, 12, 31),
            )
        }

        @Test
        fun `The generator generates each date in a range once, shrinking towards the date closest to the epoch`() {
            val samples = localDate(LocalDate.of(2024, 1, 1)..LocalDate.of(2024, 1, 3)).allSamples()

            expectThat(samples.map { it.value }).containsExactly(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 3),
                LocalDate.of(2024, 1, 2),
            )
            expectThat(samples[1].shrink().map { it.value }.first()).isEqualTo(LocalDate.of(2024, 1, 1))
        }

        @Test
        fun `The generator rejects an empty range as a configuration error`() {
            expectThrows<VarifyConfigurationException> { localDate(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 1)) }
                .get { message }.isEqualTo("range 2024-01-02..2024-01-01 must not be empty")
        }

        @Test
        fun `The generator has a different key than another date generator`() {
            expectThat(localDate().key).isNotEqualTo(localDate(max = LocalDate.of(2000, 1, 1)).key)
        }
    }

    @Nested
    inner class Years : GeneratorContract<Year>() {
        override fun subject(): Generator<Year> =
            year()

        @Test
        fun `The generator generates 1970, the bounds, and centuries with special leap year rules first`() {
            expectThat(year().firstSampleValues(5).map { it.value }).containsExactly(1970, 1900, 2100, 2000, 2099)
        }
    }

    @Nested
    inner class YearMonths : GeneratorContract<YearMonth>() {
        override fun subject(): Generator<YearMonth> =
            yearMonth()

        @Test
        fun `The generator generates the months between the bounds, including negative years`() {
            val values = yearMonth(YearMonth.of(-1, 11)..YearMonth.of(0, 2)).allSampleValues()

            expectThat(values).containsExactlyInAnyOrder(
                YearMonth.of(-1, 11),
                YearMonth.of(-1, 12),
                YearMonth.of(0, 1),
                YearMonth.of(0, 2),
            )
        }
    }

    @Nested
    inner class MonthDays : GeneratorContract<MonthDay>() {
        override fun subject(): Generator<MonthDay> =
            monthDay()

        @Test
        fun `The generator generates all days of a leap year, January 1, December 31 and February 29 first`() {
            val values = monthDay().allSampleValues()

            expectThat(values.take(3)).containsExactly(MonthDay.of(1, 1), MonthDay.of(12, 31), MonthDay.of(2, 29))
            expectThat(values.toSet()).hasSize(366)
        }
    }

    @Nested
    inner class Months : GeneratorContract<Month>() {
        override fun subject(): Generator<Month> =
            month()

        @Test
        fun `The generator generates the months of a range`() {
            expectThat(month(Month.MARCH..Month.MAY).allSampleValues())
                .containsExactly(Month.MARCH, Month.MAY, Month.APRIL)
        }
    }

    @Nested
    inner class DaysOfWeek : GeneratorContract<DayOfWeek>() {
        override fun subject(): Generator<DayOfWeek> =
            dayOfWeek()

        @Test
        fun `The generator generates all days of the week, Monday first`() {
            val values = dayOfWeek().allSampleValues()

            expectThat(values.first()).isEqualTo(DayOfWeek.MONDAY)
            expectThat(values).containsExactlyInAnyOrder(*DayOfWeek.entries.toTypedArray())
        }
    }

    @Nested
    inner class Periods : GeneratorContract<Period>() {
        override fun subject(): Generator<Period> =
            period(int(0..2), int(0..1), int(0..1))

        @Test
        fun `The generator generates the combinations of its parts, the zero period first`() {
            val values = subject().allSampleValues()

            expectThat(values.first()).isEqualTo(Period.ZERO)
            expectThat(values).hasSize(12)
        }
    }
}
