package varify.generator.util

import varify.config.*
import varify.generator.*
import varify.generator.primitive.*
import java.util.*

// A version 4 UUID has 122 random bits: 60 in the most significant half besides the version, 62 in the least
// significant half besides the variant.
private const val HIGH_BITS_LAST = (1L shl 60) - 1
private const val LOW_BITS_LAST = (1L shl 62) - 1

/**
 * Generates random (version 4) UUIDs, each once.
 *
 * The samples shrink their random bits towards zero, i.e. towards `00000000-0000-4000-8000-000000000000`.
 */
object UuidGenerator : Generator<UUID> {
    override val key = generatorKey<UuidGenerator>()

    // The UUIDs differ because their high bits do.
    override fun generate(randomizer: Randomizer): Sequence<Sample<UUID>> =
        randomizer.permutation(0..HIGH_BITS_LAST)
            .zip(randomizer.permutation(0..LOW_BITS_LAST), ::UuidSample)

    private val shrinker = BisectingLongShrinker(0)

    private class UuidSample(val highBits: Long, val lowBits: Long) : Sample<UUID>() {
        override val value: UUID = UUID(
            (highBits shl 4 and 0xFFFF_FFFF_FFFF_0000uL.toLong()) or 0x4000L or (highBits and 0x0FFFL),
            lowBits or Long.MIN_VALUE,
        )

        override fun shrink(): List<Sample<UUID>> =
            shrinker.shrink(highBits).map { UuidSample(it, lowBits) } +
                shrinker.shrink(lowBits).map { UuidSample(highBits, it) }
    }
}

@Suppress("ClassName")
object uuid {
    /** Generates random (version 4) UUIDs. */
    operator fun invoke(): UuidGenerator = UuidGenerator
}
