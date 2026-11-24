package varify.generator.primitive

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.*

class BisectingLongShrinkerTest {

    @Test
    fun `a shrinker, when called with its target value, returns no values`() {
        expectThat(BisectingLongShrinker(1).shrink(1)).isEmpty()
    }

    @Test
    fun `a shrinker halves the distance to the target from above and below`() {
        expectThat(BisectingLongShrinker(1).shrink(10)).containsExactly(1, 6, 8, 9)
        expectThat(BisectingLongShrinker(1).shrink(-2)).containsExactly(1, -1)
    }

    @Test
    fun `a shrinker, when the distance to the target exceeds the Long range, returns values inbetween`() {
        val result = BisectingLongShrinker(Long.MIN_VALUE).shrink(Long.MAX_VALUE)

        expectThat(result).hasSize(64).and {
            first().isEqualTo(Long.MIN_VALUE)
            get(1).isEqualTo(0)
            last().isEqualTo(Long.MAX_VALUE - 1)
        }
    }

    @Test
    fun `a towardsZero shrinker uses the bound closest to zero, or zero if the range contains it`() {
        expectThat(BisectingLongShrinker.towardsZero(3L..9L)).isEqualTo(BisectingLongShrinker(3))
        expectThat(BisectingLongShrinker.towardsZero(-33L..-7L)).isEqualTo(BisectingLongShrinker(-7))
        expectThat(BisectingLongShrinker.towardsZero(-1L..1L)).isEqualTo(BisectingLongShrinker(0))
    }
}
