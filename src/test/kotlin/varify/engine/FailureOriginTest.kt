package varify.engine

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*

class FailureOriginTest {

    private fun failure(block: () -> Unit): Throwable =
        runCatching(block).exceptionOrNull() ?: fail("Expected a failure")

    private fun origin(error: Throwable): FailureOrigin =
        FailureOrigin.of(error, FailureOriginTest::class.java)

    @Test
    fun `Failures of the same class on the same test line match`() {
        val (first, second) = List(2) { value -> failure { throw AssertionError("value $value") } }

        expectThat(origin(first).matches(origin(second))).isTrue()
    }

    @Test
    fun `Failures of the same class on different test lines do not match`() {
        val first = failure { throw AssertionError("first") }
        val second = failure { throw AssertionError("second") }

        expectThat(origin(first).matches(origin(second))).isFalse()
    }

    @Test
    fun `Failures of different classes on the same test line do not match`() {
        val (first, second) = List(2) { index -> failure { if (index == 0) throw AssertionError() else error("other") } }

        expectThat(origin(first).matches(origin(second))).isFalse()
    }

    @Test
    fun `Failures thrown by a helper outside the test class are identified by the test line calling it`() {
        val (first, second) = List(2) { value -> failure { AssertionHelpers.assertSmall(300 + value) } }
        val third = failure { AssertionHelpers.assertSmall(400) }

        expectThat(origin(first).matches(origin(second))).isTrue()
        expectThat(origin(first).matches(origin(third))).isFalse()
    }

    @Test
    fun `Failures without a stack trace are only compared by class`() {
        val first = AssertionError("first").apply { stackTrace = emptyArray() }
        val second = failure { throw AssertionError("second") }
        val third = failure { error("third") }

        expectThat(origin(first).matches(origin(second))).isTrue()
        expectThat(origin(second).matches(origin(first))).isTrue()
        expectThat(origin(first).matches(origin(third))).isFalse()
    }
}
