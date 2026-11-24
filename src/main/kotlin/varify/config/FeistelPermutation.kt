package varify.config

private const val ROUNDS = 4

/**
 * A pseudo-random permutation of the positions `0..lastPosition`, computed one position at a time.
 *
 * Positions are unsigned, so [lastPosition] can be `2^64 - 1` (i.e. `-1L`) to permute all 2^64 values of a `Long`.
 *
 * A Feistel network with [ROUNDS] rounds permutes the bit patterns of an even number of bits, at least as many as
 * [lastPosition] needs. Results after [lastPosition] are permuted again ("cycle walking") until they fall inside, which
 * takes fewer than four applications of the network on average because there are less than four times as many bit
 * patterns as positions.
 */
internal class FeistelPermutation(private val lastPosition: Long, keys: () -> Long) {
    private val keys = LongArray(ROUNDS) { keys() }

    // Each half has at least one bit, at most 32 bits.
    private val halfBits = (maxOf(64 - java.lang.Long.numberOfLeadingZeros(lastPosition), 1) + 1) / 2
    private val halfMask = (1L shl halfBits) - 1

    fun asSequence(): Sequence<Long> =
        sequence {
            var position = 0L
            while (true) {
                yield(get(position))
                if (position == lastPosition) break
                position++
            }
        }

    operator fun get(position: Long): Long {
        var value = permute(position)
        while (java.lang.Long.compareUnsigned(value, lastPosition) > 0) {
            value = permute(value)
        }
        return value
    }

    private fun permute(value: Long): Long {
        var left = (value ushr halfBits) and halfMask
        var right = value and halfMask
        for (key in keys) {
            val next = left xor (mix(right xor key) and halfMask)
            left = right
            right = next
        }
        return (left shl halfBits) or right
    }

    // The finalizer of SplitMix64: spreads every input bit over all output bits.
    private fun mix(value: Long): Long {
        var z = value
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
