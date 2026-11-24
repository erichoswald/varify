package varify.extensions

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*

class IntRangeExtensionsTest {

    @Test
    fun `The size of a range is the number of its elements`() {
        expectThat((1..10).size()).isEqualTo(10L)
    }

    @Test
    fun `The size of an empty range is zero`() {
        @Suppress("EmptyRange")
        val emptyRange = 1..0
        expectThat(emptyRange.size()).isEqualTo(0L)
    }

    @Test
    fun `The size of a range with more elements than Int MAX_VALUE does not overflow`() {
        expectThat((Int.MIN_VALUE..Int.MAX_VALUE).size()).isEqualTo(1L shl 32)
        expectThat((-2_000_000_000..2_000_000_000).size()).isEqualTo(4_000_000_001L)
    }
}
