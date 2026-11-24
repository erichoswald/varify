package varify.generator

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*

class DistinctSamplesTest {
    private fun withDuplicates(count: Int): Sequence<Int> =
        sequenceOf(1) + generateSequence { 1 }.take(count) + sequenceOf(2)

    @Test
    fun `A sequence continues after fewer successive duplicates than the limit`() {
        expectThat(withDuplicates(SUCCESSIVE_DUPLICATES_LIMIT - 1).distinctUntilExhausted().toList())
            .containsExactly(1, 2)
    }

    @Test
    fun `A sequence ends after as many successive duplicates as the limit`() {
        expectThat(withDuplicates(SUCCESSIVE_DUPLICATES_LIMIT).distinctUntilExhausted().toList())
            .containsExactly(1)
    }

    @Test
    fun `A new value resets the count of successive duplicates`() {
        val sequence = withDuplicates(SUCCESSIVE_DUPLICATES_LIMIT - 1) +
            generateSequence { 2 }.take(SUCCESSIVE_DUPLICATES_LIMIT - 1) + sequenceOf(3)

        expectThat(sequence.distinctUntilExhausted().toList()).containsExactly(1, 2, 3)
    }
}
