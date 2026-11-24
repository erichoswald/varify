package varify.generator.primitive

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.*

class BisectingIntShrinkerTest {

    @Test
    fun `a shrinker, when called with its target value, returns no values`() {
        val subject = BisectingIntShrinker(1)

        val result = subject.shrink(1)

        expectThat(result).isEmpty()
    }

    @Test
    fun `a shrinker, when called with a value higher than the target, halves the distance to it`() {
        val subject = BisectingIntShrinker(1)

        val result = subject.shrink(10)

        expectThat(result).containsExactly(1, 6, 8, 9)
    }

    @Test
    fun `a shrinker, when called with a value lower than the target, halves the distance to it`() {
        val subject = BisectingIntShrinker(1)

        val result = subject.shrink(-2)

        expectThat(result).containsExactly(1, -1)
    }

    @Test
    fun `a shrinker, when the distance to the target exceeds the Int range, returns values inbetween`() {
        val subject = BisectingIntShrinker(Int.MAX_VALUE)

        val result = subject.shrink(Int.MIN_VALUE)

        expectThat(result).hasSize(32).and {
            first().isEqualTo(Int.MAX_VALUE)
            last().isEqualTo(Int.MIN_VALUE + 1)
        }
    }

    @Test
    fun `a towardsZero shrinker on a range above zero uses its lower bound as target`() {
        expectThat(BisectingIntShrinker.towardsZero(3..9)).isEqualTo(BisectingIntShrinker(3))
    }

    @Test
    fun `a towardsZero shrinker on a range below zero uses its upper bound as target`() {
        expectThat(BisectingIntShrinker.towardsZero(-33..-7)).isEqualTo(BisectingIntShrinker(-7))
    }

    @Test
    fun `a towardsZero shrinker whose range contains zero uses zero as target`() {
        expectThat(BisectingIntShrinker.towardsZero(-1..1)).isEqualTo(BisectingIntShrinker(0))
    }
}
