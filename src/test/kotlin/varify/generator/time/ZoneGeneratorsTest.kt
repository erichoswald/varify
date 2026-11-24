package varify.generator.time

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*
import java.time.*
import java.time.temporal.*

class ZoneGeneratorsTest {

    private val zurich = ZoneId.of("Europe/Zurich")

    @Nested
    inner class ZoneOffsets : GeneratorContract<ZoneOffset>() {
        override fun subject(): Generator<ZoneOffset> =
            zoneOffset()

        @Test
        fun `The generator generates UTC, the bounds, and offsets that are not whole hours first`() {
            expectThat(zoneOffset().firstSampleValues(5).map(ZoneOffset::getId))
                .containsExactly("Z", "-18:00", "+18:00", "+05:30", "+05:45")
        }

        @Test
        fun `The generator generates offsets in steps of 15 minutes`() {
            val values = zoneOffset().allSampleValues()

            expectThat(values).hasSize(145)
            expectThat(values).all { get { totalSeconds % (15 * 60) }.isEqualTo(0) }
        }

        @Test
        fun `The generator rounds the bounds inwards to 15 minutes`() {
            val values = zoneOffset(ZoneOffset.ofTotalSeconds(-20 * 60), ZoneOffset.ofTotalSeconds(20 * 60))
                .allSampleValues()

            expectThat(values.map(ZoneOffset::getId)).containsExactly("Z", "-00:15", "+00:15")
        }
    }

    @Nested
    inner class ZoneIds : GeneratorContract<ZoneId>() {
        override fun subject(): Generator<ZoneId> =
            zoneId()

        @Test
        fun `The generator generates all zones of the JVM, UTC and zones with daylight saving time first`() {
            val values = zoneId().allSampleValues().map(ZoneId::getId)

            expectThat(values.take(3)).containsExactly("UTC", "Europe/Zurich", "America/New_York")
            expectThat(values.toSet()).isEqualTo(ZoneId.getAvailableZoneIds())
        }

        @Test
        fun `The generated samples shrink towards UTC`() {
            val sample = zoneId().firstSamples(2).last()

            expectThat(sample.shrink().first().value).isEqualTo(ZoneId.of("UTC"))
        }
    }

    @Nested
    inner class OffsetDateTimes : GeneratorContract<OffsetDateTime>() {
        override fun subject(): Generator<OffsetDateTime> =
            offsetDateTime()

        @Test
        fun `The generator combines the epoch and UTC first`() {
            expectThat(offsetDateTime().firstSampleValues(1))
                .containsExactly(OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
        }

        @Test
        fun `The generator generates local date-times in its range, with its offsets and precision`() {
            val range = LocalDateTime.of(2024, 1, 1, 0, 0)..LocalDateTime.of(2024, 1, 1, 0, 2)
            val offsets = from(ZoneOffset.ofHours(1), ZoneOffset.ofHours(-5))

            val values = offsetDateTime(range, offsets, ChronoUnit.MINUTES).allSampleValues()

            expectThat(values.map { it.toLocalDateTime() }.toSet())
                .isEqualTo((0L..2L).map { range.start.plusMinutes(it) }.toSet())
            expectThat(values.map { it.offset }.toSet()).isEqualTo(setOf(ZoneOffset.ofHours(1), ZoneOffset.ofHours(-5)))
            expectThat(values).hasSize(6)
        }
    }

    @Nested
    inner class ZonedDateTimes : GeneratorContract<ZonedDateTime>() {
        override fun subject(): Generator<ZonedDateTime> =
            zonedDateTime()

        @Test
        fun `The generator combines the epoch and UTC first`() {
            expectThat(zonedDateTime().firstSampleValues(1))
                .containsExactly(ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")))
        }

        @Test
        fun `The generator moves date-times in a gap forward`() {
            val gap = LocalDateTime.of(2024, 3, 31, 2, 30)

            val values = zonedDateTime(gap..gap, from(zurich)).allSampleValues()

            expectThat(values.map { it.toLocalDateTime() }.toSet())
                .containsExactly(LocalDateTime.of(2024, 3, 31, 3, 30))
        }

        @Test
        fun `The generator generates date-times around a gap`() {
            val range = LocalDateTime.of(2024, 3, 31, 1, 0)..LocalDateTime.of(2024, 3, 31, 4, 0)

            val values = zonedDateTime(range, from(zurich), ChronoUnit.MINUTES).firstSampleValues(50)

            expectThat(values).contains(
                ZonedDateTime.of(2024, 3, 31, 1, 59, 0, 0, zurich),
                ZonedDateTime.of(2024, 3, 31, 3, 0, 0, 0, zurich),
            )
        }

        @Test
        fun `The generator generates date-times with both offsets of an overlap`() {
            val range = LocalDateTime.of(2024, 10, 27, 1, 0)..LocalDateTime.of(2024, 10, 27, 4, 0)

            val values = zonedDateTime(range, from(zurich), ChronoUnit.MINUTES).firstSampleValues(50)

            expectThat(values.map { it.toOffsetDateTime() }).contains(
                OffsetDateTime.of(2024, 10, 27, 2, 59, 0, 0, ZoneOffset.ofHours(2)),
                OffsetDateTime.of(2024, 10, 27, 2, 0, 0, 0, ZoneOffset.ofHours(1)),
            )
        }

        @Test
        fun `The generator moves about a quarter of the date-times to a transition within the range`() {
            val range = LocalDateTime.of(2024, 1, 1, 0, 0)..LocalDateTime.of(2024, 6, 30, 0, 0)

            val values = zonedDateTime(range, from(zurich)).firstSampleValues(1000, Randomizer.System())

            expectThat(values).all { get { toLocalDateTime() in range }.isTrue() }
            val transition = Instant.parse("2024-03-31T01:00:00Z")
            val nearTransition = values.count {
                Duration.between(transition, it.toInstant()).abs() <= Duration.ofHours(2)
            }
            expectThat(nearTransition).isGreaterThan(150)
        }
    }
}
