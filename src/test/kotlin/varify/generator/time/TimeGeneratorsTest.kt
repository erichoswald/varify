package varify.generator.time

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*
import java.time.*
import java.time.temporal.*

class TimeGeneratorsTest {

    @Nested
    inner class Instants : GeneratorContract<Instant>() {
        override fun subject(): Generator<Instant> =
            instant()

        @Test
        fun `The generator generates the epoch, the bounds, and special instants first`() {
            expectThat(instant().firstSampleValues(8).map(Instant::toString)).containsExactly(
                "1970-01-01T00:00:00Z",
                "1900-01-01T00:00:00Z",
                "2100-12-31T23:59:59Z",
                "2000-02-29T12:00:00Z",
                "1999-12-31T23:59:59Z",
                "2000-01-01T00:00:00Z",
                "2038-01-19T03:14:07Z",
                "2038-01-19T03:14:08Z",
            )
        }

        @Test
        fun `The generator generates whole seconds by default`() {
            val values = instant().firstSampleValues(1000, Randomizer.System())

            expectThat(values.toSet()).hasSize(1000)
            expectThat(values).all { get { nano }.isEqualTo(0) }
        }

        @Test
        fun `The generator generates instants in steps of its precision`() {
            expectThat(instant(precision = ChronoUnit.MILLIS).firstSampleValues(3).last())
                .isEqualTo(Instant.parse("2100-12-31T23:59:59.999Z"))
            expectThat(instant(precision = ChronoUnit.DAYS).firstSampleValues(1000, Randomizer.System()))
                .all { get { this.toString() }.endsWith("T00:00:00Z") }
        }

        @Test
        fun `The generator rounds the bounds inwards to its precision`() {
            val values = instant(Instant.ofEpochMilli(-1500)..Instant.ofEpochMilli(1500)).allSampleValues()

            expectThat(values.map { it.epochSecond }).containsExactly(0, -1, 1)
        }

        @Test
        fun `The generator rejects a precision without an exact duration as a configuration error`() {
            expectThrows<VarifyConfigurationException> { instant(precision = ChronoUnit.MONTHS) }
                .get { message }.isEqualTo("precision Months must have an exact duration")
        }

        @Test
        fun `The generator rejects a range with too many steps as a configuration error`() {
            expectThrows<VarifyConfigurationException> { instant(Instant.MIN..Instant.MAX, ChronoUnit.NANOS) }
        }

        @Test
        fun `The generator rejects a range without a value at its precision as a configuration error`() {
            expectThrows<VarifyConfigurationException> { instant(Instant.ofEpochMilli(100)..Instant.ofEpochMilli(900)) }
                .get { message }.isEqualTo(
                    "range 1970-01-01T00:00:00.100Z..1970-01-01T00:00:00.900Z at precision Seconds must not be empty",
                )
        }

        @Test
        fun `The generated samples shrink towards the epoch`() {
            val sample = instant().firstSamples(3).last()

            expectThat(sample.shrink().first().value).isEqualTo(Instant.EPOCH)
        }

        @Test
        fun `The generator has a different key than a date-time generator with the same range`() {
            expectThat(instant().key).isNotEqualTo(localDateTime().key)
            expectThat(instant().key).isNotEqualTo(instant(precision = ChronoUnit.MILLIS).key)
        }
    }

    @Nested
    inner class LocalDateTimes : GeneratorContract<LocalDateTime>() {
        override fun subject(): Generator<LocalDateTime> =
            localDateTime()

        @Test
        fun `The generator generates the epoch, the bounds, and special date-times first`() {
            expectThat(localDateTime().firstSampleValues(4)).containsExactly(
                LocalDateTime.of(1970, 1, 1, 0, 0),
                LocalDateTime.of(1900, 1, 1, 0, 0),
                LocalDateTime.of(2100, 12, 31, 23, 59, 59),
                LocalDateTime.of(2000, 2, 29, 12, 0),
            )
        }
    }

    @Nested
    inner class LocalTimes : GeneratorContract<LocalTime>() {
        override fun subject(): Generator<LocalTime> =
            localTime()

        @Test
        fun `The generator generates midnight, the last second and noon first`() {
            expectThat(localTime().firstSampleValues(3))
                .containsExactly(LocalTime.MIDNIGHT, LocalTime.of(23, 59, 59), LocalTime.NOON)
        }

        @Test
        fun `The generator generates times in steps of its precision`() {
            expectThat(localTime(precision = ChronoUnit.HOURS).allSampleValues().toSet())
                .isEqualTo((0..23).map { LocalTime.of(it, 0) }.toSet())
        }
    }

    @Nested
    inner class Durations : GeneratorContract<Duration>() {
        override fun subject(): Generator<Duration> =
            duration()

        @Test
        fun `The generator rejects a precision without an exact duration as a configuration error`() {
            expectThrows<VarifyConfigurationException> { duration(precision = ChronoUnit.MONTHS) }
                .get { message }.isEqualTo("precision Months must have an exact duration")
        }

        @Test
        fun `The generator generates zero, the bounds, and one step in both directions first`() {
            expectThat(duration().firstSampleValues(5)).containsExactly(
                Duration.ZERO,
                Duration.ofDays(-365),
                Duration.ofDays(365),
                Duration.ofSeconds(1),
                Duration.ofSeconds(-1),
            )
        }

        @Test
        fun `The generated samples shrink towards zero`() {
            val sample = duration().firstSamples(2).last()

            expectThat(sample.shrink().map { it.value }).first().isEqualTo(Duration.ZERO)
        }

        @Test
        fun `The generator generates durations in steps of its precision`() {
            expectThat(duration(Duration.ZERO..Duration.ofNanos(3), ChronoUnit.NANOS).allSampleValues().map { it.nano })
                .containsExactly(0, 3, 1, 2)
        }
    }
}
