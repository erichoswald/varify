package varify.generator.util

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*
import java.util.*

class UuidGeneratorTest : GeneratorContract<UUID>() {

    override fun subject(): UuidGenerator =
        uuid()

    @Test
    fun `The generator generates different version 4 UUIDs`() {
        val values = uuid().firstSampleValues(1000, Randomizer.System())

        expectThat(values.toSet()).hasSize(1000)
        expectThat(values).all {
            get { version() }.isEqualTo(4)
            get { variant() }.isEqualTo(2)
        }
    }

    @Test
    fun `The generator sets all random bits`() {
        // TestRandomizer reverses when permuting, so the first sample has all random bits set.
        expectThat(uuid().firstSampleValues(1)).containsExactly(UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff"))
    }

    @Test
    fun `The generated samples shrink towards the UUID without random bits`() {
        var sample = uuid().firstSamples(1).single()
        while (sample.shrink().isNotEmpty()) {
            sample = sample.shrink().first()
        }

        expectThat(sample.value).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000000"))
    }
}
